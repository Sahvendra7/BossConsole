#!/bin/bash

# Apply database migrations to Supabase v2

set -e

echo "🗄️ Applying database migrations to Supabase v2..."

NAMESPACE="supabase"

# Get the database pod name
DB_POD=$(kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=supabase-db -o jsonpath='{.items[0].metadata.name}')

if [ -z "$DB_POD" ]; then
    echo "❌ Database pod not found!"
    exit 1
fi

echo "📍 Found database pod: $DB_POD"

# Apply each migration in order
for migration in migrations/*.sql; do
    if [ -f "$migration" ]; then
        filename=$(basename "$migration")
        echo "📝 Applying migration: $filename"
        
        # Copy migration file to pod
        kubectl cp "$migration" $NAMESPACE/$DB_POD:/tmp/$filename
        
        # Execute migration
        kubectl exec -it $DB_POD -n $NAMESPACE -- psql -U supabase_admin -d postgres -f /tmp/$filename
        
        echo "✅ Applied: $filename"
    fi
done

echo "🎉 All migrations applied successfully!"

# Show tables
echo ""
echo "📊 Database tables:"
kubectl exec -it $DB_POD -n $NAMESPACE -- psql -U supabase_admin -d postgres -c "\dt"