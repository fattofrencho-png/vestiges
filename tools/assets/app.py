"""Asset Studio as a desktop app: starts the local server and shows it in its own window.

Falls back to the default browser when pywebview isn't installed, so the .bat keeps working.
"""
import sys
import threading
import time

import studio

TITLE = "Asset Studio"


def quiet_console():
    """A windowed .exe has no console, so printing would crash; send it to a log file instead."""
    if sys.stdout is None or sys.stderr is None:
        log = open(studio.providers.HOME / "app.log", "a", encoding="utf-8", buffering=1)
        sys.stdout = sys.stdout or log
        sys.stderr = sys.stderr or log


def wait_until_up(seconds=15):
    end = time.time() + seconds
    while time.time() < end:
        if studio.already_running():
            return True
        time.sleep(0.2)
    return False


def main():
    quiet_console()
    try:
        import webview
    except ImportError:
        print("pywebview is not installed, opening in the browser instead.")
        studio.main()
        return
    if not studio.already_running():
        sys.argv.append("--no-browser")
        threading.Thread(target=studio.main, daemon=True).start()
        if not wait_until_up():
            print("Asset Studio could not start.")
            sys.exit(1)
    webview.create_window(TITLE, f"http://{studio.HOST}:{studio.PORT}", width=1400, height=900, min_size=(1000, 650))
    webview.start()


if __name__ == "__main__":
    main()
