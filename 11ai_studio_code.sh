#!/bin/bash
git init
git add -A
git commit -m "fox"
git branch -M main
git remote add origin https://github.com/Izeraa/WaylandDroid.git
git push -f origin main
