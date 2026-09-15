#!/usr/bin/env python3
import argparse
import os
import shutil
import subprocess
import sys
import time

ALL_BRANCHES = [
    "mc-26.2",
    "mc-26.1",
    "mc-1.21.11",
    "mc-1.21.10",
    "mc-1.21.8",
    "mc-1.21.4",
    "mc-1.21.1",
]

def get_mc_version(project_root):
    props = os.path.join(project_root, "gradle.properties")
    if os.path.exists(props):
        with open(props, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("minecraft_version="):
                    return line.split("=", 1)[1].strip()
    return "unknown"

def run_single_test(project_root, loader, world):
    mc_version = get_mc_version(project_root)
    mc_slug = mc_version.replace(".", "_")

    screenshot_dir = os.path.join(project_root, loader, "run", "screenshots")
    os.makedirs(screenshot_dir, exist_ok=True)
    before_files = set(os.listdir(screenshot_dir))

    task = f":{loader}:runClient"
    cmd = [
        "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
        "./gradlew", task
    ]
    if loader == "neoforge":
        cmd.append(f"-PquickPlaySingleplayer={world}")
    else:
        cmd.extend(["-x", "downloadAssets"])
        cmd.append(f"--args=--quickPlaySingleplayer \"{world}\"")
    cmd.append("-Dredfx.smokeTest=true")

    env = os.environ.copy()
    env["REDFX_SMOKE_TEST"] = "true"

    print(f"\n========================================================")
    print(f" Running in-game smoke test: MC {mc_version} ({loader})")
    print(f"========================================================")
    print("Command:", subprocess.list2cmdline(cmd))

    start_time = time.time()
    res = subprocess.run(cmd, env=env)
    elapsed = time.time() - start_time

    if res.returncode != 0:
        print(f"--> FAILED: {loader} on MC {mc_version} exited with code {res.returncode}")
        return False, None

    print(f"--> SUCCESS: {loader} on MC {mc_version} passed in {elapsed:.1f}s")

    # Locate generated screenshot
    after_files = set(os.listdir(screenshot_dir))
    new_files = [f for f in (after_files - before_files) if f.endswith(".png")]
    if not new_files and after_files:
        new_files = sorted(
            [f for f in after_files if f.endswith(".png")],
            key=lambda f: os.path.getmtime(os.path.join(screenshot_dir, f)),
            reverse=True
        )

    dest_path = None
    if new_files:
        src_path = os.path.join(screenshot_dir, new_files[0])
        release_screenshots_dir = os.path.join(project_root, "release", "screenshots")
        os.makedirs(release_screenshots_dir, exist_ok=True)

        dest_filename = f"mc_{mc_slug}_{loader}.png"
        dest_path = os.path.join(release_screenshots_dir, dest_filename)
        shutil.copy2(src_path, dest_path)
        print(f"--> Screenshot saved: {dest_path}")
    else:
        print("--> WARNING: No screenshot file found in", screenshot_dir)

    return True, dest_path

def main():
    parser = argparse.ArgumentParser(description="Run automated in-game smoke test in headless Xvfb")
    parser.add_argument("--branch", default="current", help="Git branch to test: 'current', 'all', or branch name like 'mc-26.2'")
    parser.add_argument("--all-branches", action="store_true", help="Test all 7 Minecraft version branches")
    parser.add_argument("--loader", choices=["fabric", "neoforge", "both"], default="fabric", help="Mod loader to test")
    parser.add_argument("--world", default="New World", help="Singleplayer world name")
    args = parser.parse_args()

    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(project_root)

    loaders = ["fabric", "neoforge"] if args.loader == "both" else [args.loader]

    branches_to_run = []
    if args.all_branches or args.branch == "all":
        branches_to_run = ALL_BRANCHES
    elif args.branch == "current":
        # Don't switch branch
        branches_to_run = [None]
    else:
        branches_to_run = [args.branch]

    original_branch = None
    try:
        out = subprocess.check_output(["git", "branch", "--show-current"], stderr=subprocess.DEVNULL)
        original_branch = out.decode().strip()
    except Exception:
        pass

    results = []

    try:
        for branch in branches_to_run:
            if branch is not None:
                print(f"\n>>> Checking out branch {branch}...")
                subprocess.run(["git", "checkout", branch], check=True)

            for loader in loaders:
                ok, shot = run_single_test(project_root, loader, args.world)
                mc_ver = get_mc_version(project_root)
                results.append({
                    "branch": branch or original_branch,
                    "mc_version": mc_ver,
                    "loader": loader,
                    "success": ok,
                    "screenshot": shot
                })
                if not ok:
                    print(f"Stopping test run due to failure on {branch or original_branch} ({loader})")
                    sys.exit(1)
    finally:
        if original_branch and branches_to_run != [None]:
            print(f"\nRestoring original branch: {original_branch}")
            subprocess.run(["git", "checkout", original_branch])

    print("\n" + "=" * 70)
    print(" SMOKE TEST SUMMARY")
    print("=" * 70)
    for r in results:
        status = "PASSED" if r["success"] else "FAILED"
        shot_str = r["screenshot"] if r["screenshot"] else "No screenshot"
        print(f"[{status}] MC {r['mc_version']:<8} {r['loader']:<10} -> {shot_str}")
    print("=" * 70)

if __name__ == "__main__":
    main()
