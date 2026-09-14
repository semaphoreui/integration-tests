#!/bin/sh

set -eu

repository=/repository/parallel-tasks/branch-isolation

rm -rf "$repository"
mkdir -p "$repository"
git -C "$repository" init -b main
git -C "$repository" config user.name "Semaphore Parallel Tasks Fixture"
git -C "$repository" config user.email "parallel-tasks-fixture@localhost"
touch "$repository/.gitkeep"
git -C "$repository" add .gitkeep
git -C "$repository" commit -m "Prepare branch isolation fixture"

git -C "$repository" checkout -b bookwright-branch-isolation-a
cp /fixture/branch-isolation-a.yml "$repository/branch-isolation.yml"
printf '%s\n' branch-a > "$repository/.bookwright-branch-marker"
git -C "$repository" add branch-isolation.yml .bookwright-branch-marker
git -C "$repository" commit -m "Add branch isolation fixture A"

git -C "$repository" checkout main
git -C "$repository" checkout -b bookwright-branch-isolation-b
cp /fixture/branch-isolation-b.yml "$repository/branch-isolation.yml"
printf '%s\n' branch-b > "$repository/.bookwright-branch-marker"
git -C "$repository" add branch-isolation.yml .bookwright-branch-marker
git -C "$repository" commit -m "Add branch isolation fixture B"
git -C "$repository" checkout main
chown -R 1001:0 "$repository"
