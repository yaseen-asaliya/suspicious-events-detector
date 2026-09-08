# Assignment 2: review of `upgrade_clusters.sh`

Don't ship this one as-is — it has three real bugs, and a quick test with one or two contexts can't catch
any of them:

- **`use-context` + `&` races.** `use-context` flips one shared pointer in `~/.kube/config`. The loop
  backgrounds the upgrade for cluster A, then immediately repoints that same file at cluster B — if A's
  backgrounded `kubectl` hasn't read the config yet, it picks up B's context and patches the wrong cluster.
- **`deployment/app-xyz ... --all` is a contradiction** — naming a specific resource and passing `--all` in
  the same call errors out in kubectl. Nothing checks the exit code, so the script prints "Upgrading
  cluster..." and "All clusters processed" while actually patching nothing.
- **The version check compares strings, not versions.** `[[ "$VERSION" < "1.16" ]]` does ASCII comparison,
  so `"1.9" < "1.16"` is false. Every single-digit minor version — the oldest clusters this script exists
  to find — gets treated as fine and skipped.

Fix: give each `kubectl` call its own `--context` instead of mutating one shared one, drop `&` and `--all`
so each upgrade runs on its own resource, and compare versions with `sort -V` instead of `<`.

```bash
#!/bin/bash
set -e

CONTEXTS=$(kubectl config get-contexts -o name)

for CONTEXT in $CONTEXTS
do
  VERSION=$(kubectl --context "$CONTEXT" version -o json | jq -r '.serverVersion.gitVersion' | sed 's/^v//')
  IMAGE_VERSION=$(kubectl --context "$CONTEXT" get deployment app-xyz -o jsonpath='{.spec.template.spec.containers[0].image}' | awk -F: '{print $2}')
  OLDEST=$(printf '%s\n%s' "$VERSION" "1.16" | sort -V | head -1)

  if [ "$OLDEST" != "1.16" ] && [ "$IMAGE_VERSION" == "2.3" ]
  then
    echo "Upgrading cluster $CONTEXT..."
    kubectl --context "$CONTEXT" set image deployment/app-xyz app-xyz=xyz:2.4
  fi
done

echo "All clusters processed."
```
