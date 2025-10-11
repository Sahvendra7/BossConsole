# Terraform configuration for BOSS GKE Cluster

terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}

# Variables
variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP Zone"
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  description = "GKE Cluster Name"
  type        = string
  default     = "boss-cluster"
}

variable "node_count" {
  description = "Initial node count"
  type        = number
  default     = 3
}

# Configure the Google Cloud Provider
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# Get available zones
data "google_compute_zones" "available" {
  region = var.region
}

# GKE Cluster
resource "google_container_cluster" "boss_cluster" {
  name     = var.cluster_name
  location = var.zone

  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  remove_default_node_pool = true
  initial_node_count       = 1

  # Network configuration
  network    = "default"
  subnetwork = "default"

  # Workload Identity
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  # IP allocation for VPC-native networking
  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  # Network policy (Calico)
  network_policy {
    enabled  = true
    provider = "CALICO"
  }

  # Addons
  addons_config {
    horizontal_pod_autoscaling {
      disabled = false
    }
    http_load_balancing {
      disabled = false
    }
    network_policy_config {
      disabled = false
    }
  }

  # Logging and monitoring
  logging_service    = "logging.googleapis.com/kubernetes"
  monitoring_service = "monitoring.googleapis.com/kubernetes"

  # Maintenance window
  maintenance_policy {
    daily_maintenance_window {
      start_time = "02:00"
    }
  }

  # Security
  master_auth {
    client_certificate_config {
      issue_client_certificate = false
    }
  }

  # Enable shielded nodes
  enable_shielded_nodes = true
}

# Node Pool
resource "google_container_node_pool" "boss_nodes" {
  name       = "boss-node-pool"
  location   = var.zone
  cluster    = google_container_cluster.boss_cluster.name
  node_count = var.node_count

  # Auto-scaling
  autoscaling {
    min_node_count = 1
    max_node_count = 10
  }

  # Node configuration
  node_config {
    preemptible  = false  # Set to true for cost savings in dev/test
    machine_type = "e2-standard-4"  # 4 vCPUs, 16 GB RAM
    disk_size_gb = 100
    disk_type    = "pd-standard"
    image_type   = "COS_CONTAINERD"

    # Google recommends custom service accounts that have cloud-platform scope and permissions granted via IAM Roles.
    service_account = google_service_account.boss_sa.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    # Workload Identity
    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    # Labels
    labels = {
      app       = "boss"
      node-pool = "boss-node-pool"
    }

    # Shielded VM
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
  }

  # Node management
  management {
    auto_repair  = true
    auto_upgrade = true
  }

  # Upgrade settings
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0
  }
}

# Service Account for nodes
resource "google_service_account" "boss_sa" {
  account_id   = "boss-gke-sa"
  display_name = "BOSS GKE Service Account"
  description  = "Service account for BOSS GKE cluster nodes"
}

# IAM bindings for the service account
resource "google_project_iam_binding" "boss_sa_bindings" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/stackdriver.resourceMetadata.writer"
  ])

  project = var.project_id
  role    = each.value
  members = [
    "serviceAccount:${google_service_account.boss_sa.email}"
  ]
}

# Configure kubectl
resource "null_resource" "configure_kubectl" {
  depends_on = [google_container_cluster.boss_cluster]

  provisioner "local-exec" {
    command = "gcloud container clusters get-credentials ${var.cluster_name} --zone ${var.zone} --project ${var.project_id}"
  }
}

# Outputs
output "cluster_name" {
  value = google_container_cluster.boss_cluster.name
}

output "cluster_endpoint" {
  value = google_container_cluster.boss_cluster.endpoint
}

output "cluster_ca_certificate" {
  value = google_container_cluster.boss_cluster.master_auth[0].cluster_ca_certificate
  sensitive = true
}

output "service_account_email" {
  value = google_service_account.boss_sa.email
}

output "kubectl_config" {
  value = "gcloud container clusters get-credentials ${var.cluster_name} --zone ${var.zone} --project ${var.project_id}"
}