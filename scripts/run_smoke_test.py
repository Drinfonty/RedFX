#!/usr/bin/env python3
import argparse
import os
import subprocess
import sys
import time

def main():
    parser = argparse.ArgumentParser(description="Run automated in-game smoke test in headless Xvfb")
    parser.add_argument("--branch", default="mc-26.2", help="Git branch to test (default: mc-26.2)")
    parser.add_argument("--loader", choices=["fabric", "neoforge", "both"], default="fabric", help="Mod loader to test")
    parser.add_argument("--world", default="New World", help="Singleplayer world name")
    args = parser.parse_args()

    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(project_root)

    loaders = ["fabric", "neoforge"] if args.loader == "both" else [args.loader]

    for loader in loaders:
        print(f"\n========================================================")
        print(f" Running in-game smoke test on {args.branch} ({loader})")
        print(f"========================================================")

        task = f":{loader}:runClient"
        cmd = [
            "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
            "./gradlew", task
        ]
        if loader == "neoforge":
            cmd.append(f"-PquickPlaySingleplayer={args.world}")
        else:
            cmd.append(f"--args=--quickPlaySingleplayer \"{args.world}\"")
        cmd.append("-Dredfx.smokeTest=true")

        env = os.environ.copy()
        env["REDFX_SMOKE_TEST"] = "true"

        print("Command:", subprocess.list2cmdline(cmd))
        start_time = time.time()
        res = subprocess.run(cmd, env=env)
        elapsed = time.time() - start_time

        if res.returncode == 0:
            print(f"--> SUCCESS: {loader} on {args.branch} passed in {elapsed:.1f}s")
        else:
            print(f"--> FAILED: {loader} on {args.branch} exited with code {res.returncode}")
            sys.exit(res.returncode)

if __name__ == "__main__":
    main()
