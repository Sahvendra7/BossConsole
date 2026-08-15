#!/bin/zsh
# Poll PR checks until none are pending, emitting each terminal result once.
pr="$1"
prev=""
for i in $(seq 1 80); do
  s=$(gh pr checks "$pr" --json name,bucket 2>/dev/null)
  if [ -z "$s" ]; then sleep 25; continue; fi
  cur=$(echo "$s" | jq -r '.[] | select(.bucket!="pending") | "\(.name): \(.bucket)"' | sort)
  for line in ${(f)cur}; do
    case "$prev" in
      *"$line"*) ;;
      *) echo "$line" ;;
    esac
  done
  prev="$cur"
  done_yet=$(echo "$s" | jq -r 'if (length > 0 and all(.[]; .bucket!="pending")) then "yes" else "no" end')
  if [ "$done_yet" = "yes" ]; then echo "ALL CHECKS COMPLETE"; exit 0; fi
  sleep 25
done
echo "WATCH TIMED OUT"
