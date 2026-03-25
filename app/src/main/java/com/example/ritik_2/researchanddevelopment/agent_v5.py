"""
PC Command Agent v5.0 — Windows Side
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEW in v4 (fixes all "stops working" issues):
  ✅ NEVER STOPS — auto-restart watchdog thread
  ✅ LOCK SCREEN WAKE — press any key to wake screen, then type password
  ✅ ALWAYS RECONNECTABLE — no timeout, no disconnect after long idle
  ✅ CRASH RECOVERY — Flask server auto-restarts if it crashes
  ✅ AUTO INSTALL AS SERVICE — run: python agent_v4.py --install
  ✅ KEEP-ALIVE heartbeat so mobile app always knows agent is alive
  ✅ All v3 features preserved (mouse, keyboard, files, apps, etc.)

QUICK START:
  1. pip install flask pyautogui pynput psutil pywin32 pystray pillow
  2. python agent_v4.py --install     ← installs as Windows service (run as Admin)
     OR
     python agent_v4.py              ← run manually (tray mode)

TO CONTROL LOCK SCREEN (type password from phone):
  - Agent must be installed as service (--install)
  - From phone: POST /wakescreen  →  screen wakes up
  - From phone: POST /input/keyboard/type {"value":"YourPassword"}
  - From phone: POST /input/keyboard/key {"value":"ENTER"}
"""

import json, os, subprocess, time, threading, sys, logging
import psutil, ctypes, winreg, socket, struct
import pyautogui
from flask import Flask, request, jsonify
from pynput.keyboard import Key, Controller as KeyboardController
from pynput.mouse import Button, Controller as MouseController

# ─────────────────────────────────────────────────────────────
#  CONFIG — Change these to match your Android app settings
# ─────────────────────────────────────────────────────────────
HOST       = "0.0.0.0"
PORT       = 5000
SECRET_KEY = "my_secret_123"

# ─────────────────────────────────────────────────────────────
#  IT ADMIN CONFIG
#  Set your Windows admin credentials here ONCE.
#  These are used to unlock machines remotely — same as
#  an admin physically typing at the machine.
#  Agent file stays on your local network only.
# ─────────────────────────────────────────────────────────────
ADMIN_USERNAME = ""        # e.g. "Administrator" or "DOMAIN\\admin"
ADMIN_PASSWORD = ""        # Your Windows admin password
ADMIN_DOMAIN   = "."       # "." = local machine, or "YOURDOMAIN"
# If set, /unlock works without sending password from phone
# Just call POST /unlock and agent uses these credentials

# ─────────────────────────────────────────────────────────────
#  LOGGING — logs to file so you can debug issues
# ─────────────────────────────────────────────────────────────
LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "agent_log.txt")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(LOG_PATH, encoding="utf-8"),
        logging.StreamHandler(sys.stdout),
    ]
)
log = logging.getLogger("agent")

# ─────────────────────────────────────────────────────────────
#  CRITICAL: Disable PyAutoGUI failsafe (prevents corner-stuck bug)
# ─────────────────────────────────────────────────────────────
pyautogui.FAILSAFE = False
pyautogui.PAUSE    = 0

app      = Flask(__name__)
keyboard = KeyboardController()
mouse    = MouseController()

_drag_active = False
_drag_button = Button.left

# ─────────────────────────────────────────────────────────────
#  SendInput — Works on lock screen & secure desktop
#  pynput/pyautogui use LLHOOK which Windows blocks on lock screen
#  SendInput is the ONLY method that works at kernel level
# ─────────────────────────────────────────────────────────────

INPUT_MOUSE    = 0
INPUT_KEYBOARD = 1
KEYEVENTF_KEYUP      = 0x0002
KEYEVENTF_EXTENDEDKEY = 0x0001
MOUSEEVENTF_MOVE       = 0x0001
MOUSEEVENTF_LEFTDOWN   = 0x0002
MOUSEEVENTF_LEFTUP     = 0x0004
MOUSEEVENTF_RIGHTDOWN  = 0x0008
MOUSEEVENTF_RIGHTUP    = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP   = 0x0040
MOUSEEVENTF_WHEEL      = 0x0800
MOUSEEVENTF_ABSOLUTE   = 0x8000

# Virtual key codes for SendInput
VK = {
    "WIN":    0x5B, "LWIN": 0x5B, "RWIN": 0x5C,
    "CTRL":   0x11, "ALT":  0x12, "SHIFT": 0x10,
    "ENTER":  0x0D, "ESC":  0x1B, "SPACE": 0x20,
    "TAB":    0x09, "BACK": 0x08, "DEL":   0x2E,
    "UP":     0x26, "DOWN": 0x28, "LEFT":  0x25, "RIGHT": 0x27,
    "HOME":   0x24, "END":  0x23, "PGUP":  0x21, "PGDN":  0x22,
    "F1":0x70,"F2":0x71,"F3":0x72,"F4":0x73,"F5":0x74,"F6":0x75,
    "F7":0x76,"F8":0x77,"F9":0x78,"F10":0x79,"F11":0x7A,"F12":0x7B,
    "INSERT":0x2D,"PRINTSCREEN":0x2C,"PAUSE":0x13,"NUMLOCK":0x90,
    "VOLUP":0xAF,"VOLDN":0xAE,"MUTE":0xAD,
    "A":0x41,"B":0x42,"C":0x43,"D":0x44,"E":0x45,"F":0x46,
    "G":0x47,"H":0x48,"I":0x49,"J":0x4A,"K":0x4B,"L":0x4C,
    "M":0x4D,"N":0x4E,"O":0x4F,"P":0x50,"Q":0x51,"R":0x52,
    "S":0x53,"T":0x54,"U":0x55,"V":0x56,"W":0x57,"X":0x58,
    "Y":0x59,"Z":0x5A,
}

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx",ctypes.c_long),("dy",ctypes.c_long),
                ("mouseData",ctypes.c_ulong),("dwFlags",ctypes.c_ulong),
                ("time",ctypes.c_ulong),("dwExtraInfo",ctypes.POINTER(ctypes.c_ulong))]

class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk",ctypes.c_ushort),("wScan",ctypes.c_ushort),
                ("dwFlags",ctypes.c_ulong),("time",ctypes.c_ulong),
                ("dwExtraInfo",ctypes.POINTER(ctypes.c_ulong))]

class _INPUTunion(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT)]

class INPUT(ctypes.Structure):
    _fields_ = [("type", ctypes.c_ulong), ("_input", _INPUTunion)]

def _send_key(vk: int, up: bool = False, extended: bool = False):
    """Send a single key via SendInput — works on lock screen"""
    flags = KEYEVENTF_KEYUP if up else 0
    if extended: flags |= KEYEVENTF_EXTENDEDKEY
    inp = INPUT(type=INPUT_KEYBOARD,
                _input=_INPUTunion(ki=KEYBDINPUT(wVk=vk, wScan=0, dwFlags=flags,
                                                  time=0, dwExtraInfo=None)))
    ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))

def _send_combo(*vk_codes):
    """Press multiple keys together via SendInput (e.g. WIN+L)"""
    for vk in vk_codes:
        _send_key(vk, up=False)
    for vk in reversed(vk_codes):
        _send_key(vk, up=True)
    time.sleep(0.05)

def _send_mouse_input(flags: int, dx: int = 0, dy: int = 0, data: int = 0):
    """Send mouse input via SendInput"""
    inp = INPUT(type=INPUT_MOUSE,
                _input=_INPUTunion(mi=MOUSEINPUT(dx=dx, dy=dy, mouseData=data,
                                                  dwFlags=flags, time=0, dwExtraInfo=None)))
    ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))

def _move_mouse_relative(dx: int, dy: int):
    """Move mouse by delta using SendInput (works on lock screen)"""
    _send_mouse_input(MOUSEEVENTF_MOVE, dx=dx, dy=dy)

def _move_mouse_absolute(x: int, y: int):
    """Move mouse to absolute position (0-65535 range for SendInput)"""
    sw, sh = pyautogui.size()
    abs_x = int(x * 65535 / sw)
    abs_y = int(y * 65535 / sh)
    _send_mouse_input(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE, dx=abs_x, dy=abs_y)

def _type_string_sendinput(text: str):
    """Type each character using SendInput — works on lock screen"""
    for ch in text:
        # Use VkKeyScanW to get virtual key for any Unicode char
        vk_scan = ctypes.windll.user32.VkKeyScanW(ord(ch))
        vk = vk_scan & 0xFF
        shift = (vk_scan >> 8) & 0xFF
        if vk != 0xFF:
            if shift & 1:  # Shift required
                _send_key(VK["SHIFT"])
            _send_key(vk)
            _send_key(vk, up=True)
            if shift & 1:
                _send_key(VK["SHIFT"], up=True)
        time.sleep(0.02)

# ─────────────────────────────────────────────────────────────
#  Flask config: disable timeouts, keep connections alive
# ─────────────────────────────────────────────────────────────
app.config["SEND_FILE_MAX_AGE_DEFAULT"] = 0


# ═══════════════════════════════════════════════════════
#  AUTHkya hi likhu ab
# ═══════════════════════════════════════════════════════

def _get_connection_log_path():
    """Per-session log file named with date."""
    today = time.strftime("%Y-%m-%d")
    log_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "connection_logs")
    os.makedirs(log_dir, exist_ok=True)
    return os.path.join(log_dir, f"connections_{today}.log")

def _log_connection(ip: str, path: str, device: str = ""):
    """Log each connection: time, IP, device name, endpoint accessed."""
    try:
        entry = (
            f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] "
            f"IP={ip:20s} "
            f"DEVICE={device or 'unknown':30s} "
            f"ENDPOINT={path}\n"
        )
        with open(_get_connection_log_path(), "a", encoding="utf-8") as f:
            f.write(entry)
        log.info(f"CONNECTION: {ip} | {device} | {path}")
    except Exception as e:
        log.warning(f"Could not write connection log: {e}")

@app.before_request
def check_auth():
    if request.path in ["/ping", "/"]:
        return None
    if request.headers.get("X-Secret-Key") != SECRET_KEY:
        # Log unauthorized attempts too
        ip = request.headers.get("X-Forwarded-For", request.remote_addr)
        _log_connection(ip, request.path, "UNAUTHORIZED")
        return jsonify({"error": "Unauthorized"}), 401
    # Log authorized connections
    ip     = request.headers.get("X-Forwarded-For", request.remote_addr)
    device = request.headers.get("X-Device-Name", request.headers.get("User-Agent", "")[:40])
    _log_connection(ip, request.path, device)


@app.route("/")
@app.route("/ping")
def ping():
    import platform
    return jsonify({
        "status":   "online",
        "pc_name":  os.environ.get("COMPUTERNAME", socket.gethostname()),
        "os":       platform.version(),
        "version":  "5.0",
        "uptime":   int(time.time() - _start_time),
    })


# ═══════════════════════════════════════════════════════
#  ★ NEW: WAKE SCREEN ENDPOINT
#  Call this from phone to wake PC from lock/sleep screen
#  Then type password and press ENTER
# ═══════════════════════════════════════════════════════

@app.route("/wakescreen", methods=["POST", "GET"])
def wake_screen():
    """
    Wake the PC screen from sleep/lock.
    After calling this, use /input/keyboard/type to enter password
    and /input/keyboard/key ENTER to log in.
    """
    try:
        # Method 1: Move mouse slightly (wakes most sleep states)
        cx, cy = mouse.position
        screen_w, screen_h = pyautogui.size()
        nx = max(1, min(screen_w - 2, cx + 1))
        mouse.position = (nx, cy)
        time.sleep(0.1)
        mouse.position = (cx, cy)

        # Method 2: Simulate a harmless key press (Shift key — won't type anything)
        # This wakes the screen on lock screen too
        keyboard.press(Key.shift)
        time.sleep(0.05)
        keyboard.release(Key.shift)

        # Method 3: Windows API — SetThreadExecutionState to prevent sleep
        # and SendInput to simulate activity
        try:
            # Prevent system from going back to sleep for 30 seconds
            ctypes.windll.kernel32.SetThreadExecutionState(
                0x80000000 | 0x00000001  # ES_CONTINUOUS | ES_SYSTEM_REQUIRED
            )
            # Wake display
            ctypes.windll.kernel32.SetThreadExecutionState(
                0x80000000 | 0x00000002  # ES_CONTINUOUS | ES_DISPLAY_REQUIRED
            )
        except Exception as e:
            log.warning(f"SetThreadExecutionState failed: {e}")

        # Method 4: PowerShell wake display (most reliable for deep sleep)
        try:
            ps_cmd = (
                "$wsh = New-Object -ComObject WScript.Shell; "
                "$wsh.SendKeys('%'); "  # ALT key — harmless, wakes screen
            )
            subprocess.Popen(
                ["powershell", "-WindowStyle", "Hidden", "-Command", ps_cmd],
                creationflags=subprocess.CREATE_NO_WINDOW
            )
        except Exception as e:
            log.warning(f"PowerShell wake failed: {e}")

        log.info("Screen wake requested")
        return jsonify({"ok": True, "message": "Screen wake sent. Now send /input/keyboard/type with your password."})
    except Exception as e:
        log.error(f"Wake screen error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/unlock", methods=["POST"])
def unlock_screen():
    """
    IT Admin unlock — uses stored credentials from config.
    Set ADMIN_PASSWORD in agent config for passwordless unlock from phone.
    Body (optional if ADMIN_PASSWORD set): {"password": "override"}
    """
    data     = request.get_json() or {}
    password = data.get("password", "") or ADMIN_PASSWORD
    username = data.get("username", "") or ADMIN_USERNAME
    domain   = data.get("domain",   "") or ADMIN_DOMAIN

    if not password:
        return jsonify({
            "error": "No password configured.",
            "fix"  : "Set ADMIN_PASSWORD in agent_v5.py config section"
        }), 400

    def do_unlock():
        try:
            log.info(f"Unlock starting...")
            # Wake display
            _wake_display_now()
            time.sleep(1.0)
            # Dismiss overlay
            _send_key(VK["ESC"]); time.sleep(0.05); _send_key(VK["ESC"], up=True)
            time.sleep(0.2)
            _send_mouse_input(MOUSEEVENTF_LEFTDOWN); time.sleep(0.05)
            _send_mouse_input(MOUSEEVENTF_LEFTUP); time.sleep(0.6)
            # Focus password field
            sw, sh = pyautogui.size()
            _move_mouse_absolute(sw // 2, int(sh * 0.62))
            time.sleep(0.1)
            _send_mouse_input(MOUSEEVENTF_LEFTDOWN); time.sleep(0.05)
            _send_mouse_input(MOUSEEVENTF_LEFTUP); time.sleep(0.4)
            # Clear and type password via SendInput
            _send_combo(VK["CTRL"], VK["A"]); time.sleep(0.1)
            _send_key(VK["BACK"]); time.sleep(0.05); _send_key(VK["BACK"], up=True)
            time.sleep(0.1)
            for ch in password:
                vk_scan = ctypes.windll.user32.VkKeyScanW(ord(ch))
                vk    = vk_scan & 0xFF
                shift = (vk_scan >> 8) & 0xFF
                if vk not in (0xFF, 0):
                    if shift & 1: _send_key(VK["SHIFT"])
                    _send_key(vk); time.sleep(0.01)
                    _send_key(vk, up=True)
                    if shift & 1: _send_key(VK["SHIFT"], up=True)
                else:
                    KEYEVENTF_UNICODE = 0x0004
                    for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
                        inp = INPUT(INPUT_KEYBOARD,
                                    _INPUTunion(ki=KEYBDINPUT(0, ord(ch), flags, 0, None)))
                        ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))
                time.sleep(0.03)
            time.sleep(0.15)
            _send_key(VK["ENTER"]); time.sleep(0.05); _send_key(VK["ENTER"], up=True)
            log.info(f"✅ Unlock complete")
        except Exception as e:
            log.error(f"Unlock error: {e}")

    threading.Thread(target=do_unlock, daemon=True).start()
    return jsonify({"ok": True, "message": "Unlock started"})


@app.route("/admin/autologin/enable", methods=["POST"])
def admin_autologin_enable():
    """
    IT Admin: Enable auto-login on this machine.
    Machine will wake/boot without password prompt.
    Body: {"username":"admin","password":"pass","domain":"."}
    """
    data     = request.get_json() or {}
    username = data.get("username", ADMIN_USERNAME)
    password = data.get("password", ADMIN_PASSWORD)
    domain   = data.get("domain",   ADMIN_DOMAIN)
    if not username or not password:
        return jsonify({"error": "username and password required"}), 400
    try:
        key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                             r"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon",
                             0, winreg.KEY_SET_VALUE)
        winreg.SetValueEx(key, "AutoAdminLogon",    0, winreg.REG_SZ, "1")
        winreg.SetValueEx(key, "DefaultUsername",   0, winreg.REG_SZ, username)
        winreg.SetValueEx(key, "DefaultPassword",   0, winreg.REG_SZ, password)
        winreg.SetValueEx(key, "DefaultDomainName", 0, winreg.REG_SZ, domain)
        winreg.SetValueEx(key, "ForceAutoLogon",    0, winreg.REG_SZ, "1")
        winreg.CloseKey(key)
        log.info(f"Auto-login enabled for {username}")
        return jsonify({"ok": True, "message": f"Auto-login enabled for {username}. Machine will log in on wake/boot without password prompt."})
    except PermissionError:
        return jsonify({"ok": False, "error": "Permission denied. Run agent as Administrator: python agent_v5.py --install"}), 403
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/admin/autologin/disable", methods=["POST"])
def admin_autologin_disable():
    """Disable auto-login and remove stored password from registry."""
    try:
        key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                             r"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon",
                             0, winreg.KEY_SET_VALUE)
        winreg.SetValueEx(key, "AutoAdminLogon", 0, winreg.REG_SZ, "0")
        winreg.SetValueEx(key, "ForceAutoLogon", 0, winreg.REG_SZ, "0")
        try: winreg.DeleteValue(key, "DefaultPassword")
        except: pass
        winreg.CloseKey(key)
        return jsonify({"ok": True, "message": "Auto-login disabled, password removed from registry."})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/admin/status", methods=["GET"])
def admin_status():
    """Check admin configuration status."""
    autologin = False
    try:
        key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                             r"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon")
        autologin = winreg.QueryValueEx(key, "AutoAdminLogon")[0] == "1"
        winreg.CloseKey(key)
    except: pass
    is_system = False
    try:
        import win32security, win32api, win32con
        token = win32security.OpenProcessToken(win32api.GetCurrentProcess(), win32con.TOKEN_QUERY)
        sid   = win32security.GetTokenInformation(token, win32security.TokenUser)[0]
        is_system = str(sid) == "S-1-5-18"
    except: pass
    return jsonify({
        "ok"               : True,
        "is_system_service": is_system,
        "autologin_enabled": autologin,
        "credentials_set"  : bool(ADMIN_PASSWORD),
        "can_unlock"       : is_system,
        "tips": [] if is_system else ["Install as service for lock screen: python agent_v5.py --install"]
    })

@app.route("/screen/snapshot")
def screen_snapshot():
    """
    Returns a compressed screenshot as base64 JPEG.
    Android app polls this every 2-3 seconds for live display.
    Quality=40 keeps it small and fast over WiFi.
    """
    try:
        import base64, io
        from PIL import Image
        img = pyautogui.screenshot()
        # Resize to 480p for speed
        sw, sh = img.size
        scale = 480 / sh
        new_w = int(sw * scale)
        img = img.resize((new_w, 480), Image.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=35, optimize=True)
        b64 = base64.b64encode(buf.getvalue()).decode()
        return jsonify({
            "ok":   True,
            "data": b64,
            "w":    new_w,
            "h":    480,
            "ts":   int(time.time())
        })
    except Exception as e:
        log.error(f"Snapshot error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/screen/info")
def screen_info():
    """Returns screen size, cursor position, and active window title."""
    try:
        import ctypes as _ct
        sw, sh = pyautogui.size()
        cx, cy = mouse.position

        # Get active window title
        try:
            hwnd = _ct.windll.user32.GetForegroundWindow()
            buf  = _ct.create_unicode_buffer(256)
            _ct.windll.user32.GetWindowTextW(hwnd, buf, 256)
            title = buf.value or "Unknown"
        except:
            title = "Unknown"

        return jsonify({
            "ok":     True,
            "sw":     sw,
            "sh":     sh,
            "cx":     cx,
            "cy":     cy,
            "window": title,
            "ts":     int(time.time())
        })
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/screen/cursor", methods=["POST"])
def screen_cursor():
    """Move cursor to specific screen coordinates (absolute)."""
    data = request.get_json()
    x = int(data.get("x", 0))
    y = int(data.get("y", 0))
    _move_mouse_absolute(x, y)
    return jsonify({"ok": True})


# ═══════════════════════════════════════════════════════
#  HELPER: Smart App Launcher (unchanged from v3)
# ═══════════════════════════════════════════════════════

KNOWN_APPS = {
    "vlc":        r"C:\Program Files\VideoLAN\VLC\vlc.exe",
    "vlc.exe":    r"C:\Program Files\VideoLAN\VLC\vlc.exe",
    "word":       r"C:\Program Files\Microsoft Office\root\Office16\WINWORD.EXE",
    "winword":    r"C:\Program Files\Microsoft Office\root\Office16\WINWORD.EXE",
    "winword.exe":r"C:\Program Files\Microsoft Office\root\Office16\WINWORD.EXE",
    "excel":      r"C:\Program Files\Microsoft Office\root\Office16\EXCEL.EXE",
    "excel.exe":  r"C:\Program Files\Microsoft Office\root\Office16\EXCEL.EXE",
    "powerpoint": r"C:\Program Files\Microsoft Office\root\Office16\POWERPNT.EXE",
    "powerpnt":   r"C:\Program Files\Microsoft Office\root\Office16\POWERPNT.EXE",
    "powerpnt.exe":r"C:\Program Files\Microsoft Office\root\Office16\POWERPNT.EXE",
    "outlook":    r"C:\Program Files\Microsoft Office\root\Office16\OUTLOOK.EXE",
    "onenote":    r"C:\Program Files\Microsoft Office\root\Office16\ONENOTE.EXE",
    "access":     r"C:\Program Files\Microsoft Office\root\Office16\MSACCESS.EXE",
    "notepad":    "notepad.exe",
    "calc":       "calc.exe",
    "explorer":   "explorer.exe",
    "cmd":        "cmd.exe",
    "chrome":     r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    "firefox":    r"C:\Program Files\Mozilla Firefox\firefox.exe",
}

OFFICE_SEARCH_PATHS = [
    r"C:\Program Files\Microsoft Office\root\Office16",
    r"C:\Program Files\Microsoft Office\Office16",
    r"C:\Program Files (x86)\Microsoft Office\root\Office16",
    r"C:\Program Files (x86)\Microsoft Office\Office16",
    r"C:\Program Files\Microsoft Office\root\Office15",
    r"C:\Program Files\Microsoft Office\Office15",
]

def resolve_app_path(app_path: str) -> str:
    if not app_path:
        return app_path
    basename = os.path.basename(app_path).lower().replace(".exe", "")
    fullname = os.path.basename(app_path).lower()
    for key, override in KNOWN_APPS.items():
        if key == basename or key == fullname:
            if os.path.exists(override):
                return override
            if "office16" in override.lower() or "office15" in override.lower():
                exe_name = os.path.basename(override)
                for search_path in OFFICE_SEARCH_PATHS:
                    candidate = os.path.join(search_path, exe_name)
                    if os.path.exists(candidate):
                        return candidate
    if os.path.exists(app_path):
        name_lower = app_path.lower()
        if any(x in name_lower for x in ["uninstall","uninst","setup","install"]):
            folder = os.path.dirname(app_path)
            for f in os.listdir(folder):
                if f.lower().endswith(".exe") and not any(
                    x in f.lower() for x in ["uninstall","uninst","setup","install","update"]
                ):
                    return os.path.join(folder, f)
        return app_path
    return app_path


# ═══════════════════════════════════════════════════════
#  STEP EXECUTORS (all from v3, unchanged)
# ═══════════════════════════════════════════════════════

def execute_launch_app(step):
    raw_path = step.get("value", "")
    args     = step.get("args", [])
    path     = resolve_app_path(raw_path)
    try:
        if args:
            file_path = args[0].replace("/", "\\")
            app_path  = path.replace("/", "\\")
            result = ctypes.windll.shell32.ShellExecuteW(
                None, "open", app_path, f'"{file_path}"', None, 1)
            if result <= 32:
                subprocess.Popen([app_path, file_path], shell=False)
        else:
            path_norm = path.replace("/", "\\")
            os.startfile(path_norm)
    except Exception as e:
        try:
            subprocess.Popen([path] + args, shell=True)
        except Exception as e2:
            log.error(f"All launch methods failed: {e2}")
    return f"Launched: {path}"


def execute_kill_app(step):
    name = step.get("value", "")
    killed = False
    for proc in psutil.process_iter(['name', 'pid']):
        try:
            if proc.info['name'] and name.lower() in proc.info['name'].lower():
                proc.kill()
                killed = True
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    return f"{'Killed' if killed else 'Not found'}: {name}"


def execute_key_press(step):
    """
    KEY PRESS — Uses SendInput for ALL keys.
    SendInput works on lock screen, secure desktop, UAC prompts.
    pynput/pyautogui do NOT work there.
    """
    key_str = step.get("value", "").upper().strip()

    # Map key string → SendInput combo
    COMBOS = {
        # Windows key combos — ONLY work via SendInput (pynput can't do these)
        "WIN+L":           (VK["WIN"], VK["L"]),    # Lock PC
        "WIN+D":           (VK["WIN"], VK["D"]),    # Desktop
        "WIN+E":           (VK["WIN"], VK["E"]),    # Explorer
        "WIN+R":           (VK["WIN"], VK["R"]),    # Run
        "WIN+I":           (VK["WIN"], VK["I"]),    # Settings
        "WIN+A":           (VK["WIN"], VK["A"]),    # Action center
        "WIN+S":           (VK["WIN"], VK["S"]),    # Search
        "WIN+X":           (VK["WIN"], VK["X"]),    # Power menu
        "WIN+TAB":         (VK["WIN"], 0x09),        # Task view
        "WIN+UP":          (VK["WIN"], VK["UP"]),
        "WIN+DOWN":        (VK["WIN"], VK["DOWN"]),
        "WIN+LEFT":        (VK["WIN"], VK["LEFT"]),
        "WIN+RIGHT":       (VK["WIN"], VK["RIGHT"]),
        "WIN+SHIFT+S":     (VK["WIN"], VK["SHIFT"], VK["S"]),  # Snip
        "WIN":             (VK["WIN"],),

        # Ctrl combos
        "CTRL+C":          (VK["CTRL"], VK["C"]),
        "CTRL+V":          (VK["CTRL"], VK["V"]),
        "CTRL+Z":          (VK["CTRL"], VK["Z"]),
        "CTRL+Y":          (VK["CTRL"], VK["Y"]),
        "CTRL+S":          (VK["CTRL"], VK["S"]),
        "CTRL+A":          (VK["CTRL"], VK["A"]),
        "CTRL+X":          (VK["CTRL"], VK["X"]),
        "CTRL+W":          (VK["CTRL"], VK["W"]),
        "CTRL+N":          (VK["CTRL"], VK["N"]),
        "CTRL+T":          (VK["CTRL"], VK["T"]),
        "CTRL+F":          (VK["CTRL"], VK["F"]),
        "CTRL+P":          (VK["CTRL"], VK["P"]),
        "CTRL+O":          (VK["CTRL"], VK["O"]),
        "CTRL+ALT+DEL":    (VK["CTRL"], VK["ALT"], VK["DEL"]),
        "CTRL+SHIFT+ESC":  (VK["CTRL"], VK["SHIFT"], 0x1B),

        # Alt combos
        "ALT+F4":          (VK["ALT"], VK["F4"]),
        "ALT+TAB":         (VK["ALT"], 0x09),
        "ALT+ENTER":       (VK["ALT"], VK["ENTER"]),
        "ALT+ESC":         (VK["ALT"], VK["ESC"]),

        # Single special keys
        "ENTER":           (VK["ENTER"],),
        "ESC":             (VK["ESC"],),
        "SPACE":           (VK["SPACE"],),
        "TAB":             (0x09,),
        "BACKSPACE":       (VK["BACK"],),
        "DELETE":          (VK["DEL"],),
        "UP":              (VK["UP"],),
        "DOWN":            (VK["DOWN"],),
        "LEFT":            (VK["LEFT"],),
        "RIGHT":           (VK["RIGHT"],),
        "HOME":            (VK["HOME"],),
        "END":             (VK["END"],),
        "PAGE_UP":         (VK["PGUP"],),
        "PAGE_DOWN":       (VK["PGDN"],),
        "PRINTSCREEN":     (VK["PRINTSCREEN"],),
        "INSERT":          (VK["INSERT"],),
        "SHIFT":           (VK["SHIFT"],),
        "CTRL":            (VK["CTRL"],),
        "ALT":             (VK["ALT"],),

        # Volume (media keys)
        "VOLUME_UP":       (VK["VOLUP"],),
        "VOLUME_DOWN":     (VK["VOLDN"],),
        "MUTE":            (VK["MUTE"],),

        # Function keys
        "F1":(VK["F1"],),"F2":(VK["F2"],),"F3":(VK["F3"],),"F4":(VK["F4"],),
        "F5":(VK["F5"],),"F6":(VK["F6"],),"F7":(VK["F7"],),"F8":(VK["F8"],),
        "F9":(VK["F9"],),"F10":(VK["F10"],),"F11":(VK["F11"],),"F12":(VK["F12"],),
    }

    combo = COMBOS.get(key_str)
    if combo:
        _send_combo(*combo)
        log.info(f"Key (SendInput): {key_str}")
        return f"Key: {key_str}"

    # Single letter/number — use VkKeyScanW
    if len(key_str) == 1:
        vk = ctypes.windll.user32.VkKeyScanW(ord(key_str))
        if vk != -1:
            _send_combo(vk & 0xFF)
            return f"Key: {key_str}"

    log.warning(f"Unknown key: {key_str}")
    return f"Key unknown: {key_str}"


def execute_type_text(step):
    text = step.get("value", "")
    try:
        import win32clipboard
        win32clipboard.OpenClipboard()
        win32clipboard.EmptyClipboard()
        win32clipboard.SetClipboardText(text, win32clipboard.CF_UNICODETEXT)
        win32clipboard.CloseClipboard()
        time.sleep(0.1)
        with keyboard.pressed(Key.ctrl):
            keyboard.press("v"); keyboard.release("v")
    except ImportError:
        pyautogui.typewrite(text, interval=0.03)
    return f"Typed: {text[:30]}..."


def execute_mouse_click(step):
    x, y   = step.get("x", 0), step.get("y", 0)
    btn    = step.get("button", "left")
    double = step.get("double", False)
    pyautogui.moveTo(x, y, duration=0.1)
    if double: pyautogui.doubleClick(x, y)
    else:      pyautogui.click(x, y, button=btn)
    return f"Click ({x},{y})"


def execute_mouse_move(step):
    pyautogui.moveTo(step.get("x", 0), step.get("y", 0), duration=0.15)
    return "Moved mouse"


def execute_mouse_scroll(step):
    amount = step.get("amount", 3)
    x = step.get("x", None)
    y = step.get("y", None)
    if x and y:
        pyautogui.scroll(amount, x=x, y=y)
    else:
        pyautogui.scroll(amount)
    return f"Scrolled {amount}"


def execute_run_script(step):
    p   = step.get("value", "")
    ext = os.path.splitext(p)[1].lower()
    if ext == ".py":
        subprocess.Popen(["python", p], creationflags=subprocess.CREATE_NO_WINDOW)
    elif ext == ".bat":
        subprocess.Popen([p], shell=True)
    elif ext == ".ps1":
        subprocess.Popen(
            ["powershell", "-ExecutionPolicy", "Bypass", "-File", p],
            creationflags=subprocess.CREATE_NO_WINDOW
        )
    return f"Script: {p}"


def execute_file_op(step):
    import shutil
    action = step.get("action", "").upper()
    src, dst = step.get("from", ""), step.get("to", "")
    if action == "COPY":   shutil.copy2(src, dst)
    elif action == "MOVE": shutil.move(src, dst)
    elif action == "DELETE":
        os.remove(src) if os.path.isfile(src) else shutil.rmtree(src)
    elif action == "MKDIR": os.makedirs(src, exist_ok=True)
    elif action == "RENAME": os.rename(src, dst)
    return f"File {action}: {src}"


def execute_system_cmd(step):
    cmd  = step.get("value", "").upper()
    args = step.get("args", [])
    if cmd == "WAKE_SCREEN":
        # Wake PC from sleep/lock screen — same as /wakescreen endpoint
        keyboard.press(Key.shift); time.sleep(0.05); keyboard.release(Key.shift)
        try:
            ctypes.windll.kernel32.SetThreadExecutionState(0x80000000 | 0x00000002)
        except: pass
        return f"SysCmd: WAKE_SCREEN"
    elif cmd == "LOCK":
        ctypes.windll.user32.LockWorkStation()
    elif cmd == "SLEEP":
        os.system("rundll32.exe powrprof.dll,SetSuspendState 0,1,0")
    elif cmd == "SHUTDOWN":
        delay = args[0] if args else "0"
        os.system(f"shutdown /s /t {delay}")
    elif cmd == "RESTART":
        os.system("shutdown /r /t 0")
    elif cmd == "ABORT_SHUTDOWN":
        os.system("shutdown /a")
    elif cmd == "VOLUME_UP":
        pyautogui.press("volumeup")
    elif cmd == "VOLUME_DOWN":
        pyautogui.press("volumedown")
    elif cmd == "MUTE":
        pyautogui.press("volumemute")
    elif cmd == "VOLUME_SET":
        level = int(args[0]) if args else 50
        current_script = f'$wsh = New-Object -ComObject WScript.Shell; for($i=0;$i -lt 50;$i++){{$wsh.SendKeys([char]174)}}; for($i=0;$i -lt {level//2};$i++){{$wsh.SendKeys([char]175)}}'
        subprocess.Popen(["powershell", "-Command", current_script],
                        creationflags=subprocess.CREATE_NO_WINDOW)
    elif cmd == "OPEN_FILE":
        file_path = (args[0] if args else "").replace("/", "\\")
        if file_path and os.path.exists(file_path):
            # Use ShellExecuteW for best app association (media, docs, etc.)
            result = ctypes.windll.shell32.ShellExecuteW(None, "open", file_path, None, None, 1)
            if result <= 32:
                # Fallback to os.startfile
                os.startfile(file_path)
            log.info(f"Opened file: {file_path}")
        else:
            log.warning(f"File not found: {file_path}")
        return f"Opened: {file_path}"
    elif cmd == "PLAY_MEDIA":
        # Open media file — same as OPEN_FILE but semantically clearer
        file_path = (args[0] if args else "").replace("/", "\\")
        if file_path and os.path.exists(file_path):
            ctypes.windll.shell32.ShellExecuteW(None, "open", file_path, None, None, 1)
            log.info(f"Playing media: {file_path}")
        return f"Playing: {file_path}"
    elif cmd == "SCREENSHOT":
        path = args[0] if args else os.path.join(
            os.path.expanduser("~"), "Desktop", f"screenshot_{int(time.time())}.png")
        img = pyautogui.screenshot()
        img.save(path)
    elif cmd == "OPEN_URL":
        url = args[0] if args else "https://google.com"
        os.startfile(url)
    elif cmd == "OPEN_FOLDER":
        path = (args[0] if args else "C:/").replace("/", "\\")
        if os.path.isfile(path):
            subprocess.Popen(["explorer", "/select,", path])
        else:
            subprocess.Popen(["explorer", path])
    elif cmd == "WIN_R":
        command = args[0] if args else ""
        with keyboard.pressed(Key.cmd):
            keyboard.press("r"); keyboard.release("r")
        time.sleep(0.5)
        if command:
            pyautogui.typewrite(command, interval=0.05)
            time.sleep(0.2)
            pyautogui.press("enter")
    elif cmd == "TASK_MANAGER":
        subprocess.Popen(["taskmgr.exe"])
    elif cmd == "SETTINGS":
        subprocess.Popen(["ms-settings:"], shell=True)
    elif cmd == "CONTROL_PANEL":
        subprocess.Popen(["control.exe"])
    return f"SysCmd: {cmd}"


def execute_wait(step):
    ms = step.get("ms", 1000)
    time.sleep(ms / 1000)
    return f"Waited {ms}ms"


STEP_HANDLERS = {
    "LAUNCH_APP":   execute_launch_app,
    "KILL_APP":     execute_kill_app,
    "KEY_PRESS":    execute_key_press,
    "TYPE_TEXT":    execute_type_text,
    "MOUSE_CLICK":  execute_mouse_click,
    "MOUSE_MOVE":   execute_mouse_move,
    "MOUSE_SCROLL": execute_mouse_scroll,
    "RUN_SCRIPT":   execute_run_script,
    "FILE_OP":      execute_file_op,
    "SYSTEM_CMD":   execute_system_cmd,
    "WAIT":         execute_wait,
}


def execute_plan(plan):
    results = []
    for i, step in enumerate(plan.get("steps", [])):
        st = step.get("type", "").upper()
        handler = STEP_HANDLERS.get(st)
        try:
            r = handler(step) if handler else f"Unknown type: {st}"
            results.append({"step": i+1, "status": "OK", "result": r})
        except Exception as e:
            results.append({"step": i+1, "status": "ERROR", "error": str(e)})
    return {"steps_executed": len(results), "results": results}


# ═══════════════════════════════════════════════════════
#  PLAN ENDPOINTS
# ═══════════════════════════════════════════════════════

@app.route("/execute", methods=["POST"])
def execute():
    plan = request.get_json()
    threading.Thread(target=execute_plan, args=(plan,), daemon=True).start()
    return jsonify({"status": "executing", "plan": plan.get("planName")}), 200


@app.route("/quick", methods=["POST"])
def quick():
    step = request.get_json()
    st   = step.get("type", "").upper()
    handler = STEP_HANDLERS.get(st)
    if not handler:
        return jsonify({"error": f"Unknown step type: {st}"}), 400
    try:
        return jsonify({"status": "ok", "result": handler(step)})
    except Exception as e:
        return jsonify({"status": "error", "error": str(e)}), 500


@app.route("/processes")
def processes():
    procs = sorted(set(
        p.info['name'] for p in psutil.process_iter(['name'])
        if p.info['name']
    ))
    return jsonify({"processes": procs})


@app.route("/screen_size")
def screen_size():
    s = pyautogui.size()
    return jsonify({"width": s.width, "height": s.height})


@app.route("/screen/capture")
def screen_capture():
    """
    Returns a compressed JPEG screenshot as base64.
    Android polls this every 1-3 seconds for live preview.
    Quality param: ?q=30 (default 25, range 10-80)
    Scale param:   ?s=4  (default 4 = 1/4 size, range 2-8)
    """
    try:
        import base64
        from io import BytesIO
        from PIL import Image

        quality = max(10, min(80, int(request.args.get("q", 25))))
        scale   = max(2,  min(8,  int(request.args.get("s", 4))))

        img = pyautogui.screenshot()
        w, h = img.size
        img  = img.resize((w // scale, h // scale), Image.LANCZOS)

        buf = BytesIO()
        img.save(buf, format="JPEG", quality=quality, optimize=True)
        buf.seek(0)

        b64 = base64.b64encode(buf.read()).decode("utf-8")
        return jsonify({
            "ok":    True,
            "data": b64,   # matches ApiClient expectation
            "w":     w // scale,
            "h":     h // scale,
            "ts":    int(time.time() * 1000)
        })
    except Exception as e:
        log.error(f"Screen capture error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


# ═══════════════════════════════════════════════════════
#  BROWSE ENDPOINTS (Real-time, no caching)
# ═══════════════════════════════════════════════════════

@app.route("/browse/special")
def browse_special():
    """
    Returns special user folders: Desktop, Downloads, Documents, Pictures, Videos, Music.
    Shown in file browser for quick access to common locations.
    """
    import shutil
    folders = []
    specials = [
        ("Desktop",   os.path.join(os.path.expanduser("~"), "Desktop"),   "🖥️"),
        ("Downloads", os.path.join(os.path.expanduser("~"), "Downloads"), "⬇️"),
        ("Documents", os.path.join(os.path.expanduser("~"), "Documents"), "📄"),
        ("Pictures",  os.path.join(os.path.expanduser("~"), "Pictures"),  "🖼️"),
        ("Videos",    os.path.join(os.path.expanduser("~"), "Videos"),    "🎬"),
        ("Music",     os.path.join(os.path.expanduser("~"), "Music"),     "🎵"),
    ]
    for name, path, icon in specials:
        if os.path.exists(path):
            try:
                total, used, free = shutil.disk_usage(path)
                count = len(os.listdir(path))
            except:
                count = 0
            folders.append({
                "name": name,
                "path": path.replace("\\", "/"),
                "icon": icon,
                "count": count
            })
    response = jsonify(folders)
    response.headers["Cache-Control"] = "no-store"
    return response


@app.route("/browse/drives")
def browse_drives():
    import shutil
    drives = []
    for letter in "CDEFGHIJKLMNOPQRSTUVWXYZ":
        path = f"{letter}:\\"
        if os.path.exists(path):
            try:
                total, used, free = shutil.disk_usage(path)
                try:
                    label_buf = ctypes.create_unicode_buffer(256)
                    ctypes.windll.kernel32.GetVolumeInformationW(
                        path, label_buf, 256, None, None, None, None, 0)
                    lbl = label_buf.value or "Local Disk"
                except:
                    lbl = "Local Disk"
                drives.append({
                    "letter":  letter,
                    "label":   lbl,
                    "freeGb":  round(free  / (1024**3), 1),
                    "totalGb": round(total / (1024**3), 1),
                    "usedGb":  round(used  / (1024**3), 1),
                })
            except:
                pass
    response = jsonify(drives)
    response.headers["Cache-Control"] = "no-store"
    return response


@app.route("/browse/dir")
def browse_dir():
    path       = request.args.get("path", "C:\\").replace("/", "\\")
    exts_param = request.args.get("exts", "")
    allowed    = [e.lower() for e in exts_param.split(",") if e] if exts_param else []
    if not os.path.exists(path):
        return jsonify({"error": "Path not found"}), 404
    items = []
    try:
        entries = list(os.scandir(path))
        entries.sort(key=lambda e: (not e.is_dir(follow_symlinks=False), e.name.lower()))
        for entry in entries:
            try:
                is_dir = entry.is_dir(follow_symlinks=False)
                name   = entry.name
                ext    = "" if is_dir else os.path.splitext(name)[1][1:].lower()
                if not is_dir and allowed and ext not in allowed:
                    continue
                size_kb = mod_time = 0
                try:
                    stat     = entry.stat()
                    size_kb  = stat.st_size // 1024
                    mod_time = int(stat.st_mtime)
                except:
                    pass
                items.append({
                    "name":      name,
                    "path":      entry.path.replace("\\", "/"),
                    "isDir":     is_dir,
                    "sizeKb":    size_kb,
                    "extension": ext,
                    "modTime":   mod_time,
                })
            except:
                pass
    except PermissionError:
        return jsonify({"error": "Permission denied"}), 403
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    response = jsonify(items)
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"
    return response


def _scan_start_menu():
    """Scan Windows Start Menu folders for .lnk shortcuts — the real installed apps list."""
    import glob
    lnk_files = []
    search_paths = [
        os.path.join(os.environ.get("APPDATA",""),
                     "Microsoft","Windows","Start Menu","Programs"),
        r"C:\ProgramData\Microsoft\Windows\Start Menu\Programs",
    ]
    for base in search_paths:
        if os.path.exists(base):
            for lnk in glob.glob(os.path.join(base, "**", "*.lnk"), recursive=True):
                lnk_files.append(lnk)
    return lnk_files

def _resolve_lnk(lnk_path: str):
    """Resolve a .lnk file to its target exe path using Windows Shell API."""
    try:
        import win32com.client
        shell   = win32com.client.Dispatch("WScript.Shell")
        shortcut = shell.CreateShortcut(lnk_path)
        target  = shortcut.TargetPath
        return target if target else None
    except Exception:
        return None

def _app_icon(name: str) -> str:
    n = name.lower()
    if "chrome"     in n: return "🌐"
    if "firefox"    in n: return "🦊"
    if "edge"       in n: return "🌐"
    if "vlc"        in n: return "🎬"
    if "spotify"    in n: return "🎵"
    if "code"       in n: return "💻"
    if "word"       in n: return "📝"
    if "excel"      in n: return "📗"
    if "powerpoint" in n: return "📊"
    if "outlook"    in n: return "📧"
    if "teams"      in n: return "💬"
    if "zoom"       in n: return "📹"
    if "android"    in n: return "🤖"
    if "notepad"    in n: return "📄"
    if "calc"       in n: return "🔢"
    if "paint"      in n: return "🎨"
    if "explorer"   in n: return "📁"
    if "cmd"        in n: return "⬛"
    if "terminal"   in n: return "⬛"
    if "steam"      in n: return "🎮"
    if "discord"    in n: return "💬"
    if "photoshop"  in n: return "🖼️"
    if "acrobat"    in n: return "📑"
    return "📦"

@app.route("/browse/apps")
def browse_apps():
    """Scan real Windows Start Menu for installed apps."""
    running_names = {
        p.info["name"].lower()
        for p in psutil.process_iter(["name"])
        if p.info["name"]
    }
    apps = []
    seen_names = set()
    lnk_files = _scan_start_menu()

    for lnk_path in lnk_files:
        try:
            name = os.path.splitext(os.path.basename(lnk_path))[0]
            if not name or name.lower() in seen_names:
                continue
            # Skip uninstall/help entries
            skip_words = ["uninstall","readme","help","documentation","release notes","license"]
            if any(w in name.lower() for w in skip_words):
                continue

            # Try to resolve to exe
            exe = _resolve_lnk(lnk_path) or lnk_path

            seen_names.add(name.lower())
            exe_name = os.path.basename(exe).lower()
            is_running = exe_name in running_names

            apps.append({
                "name"     : name,
                "exePath"  : exe if exe != lnk_path else lnk_path,
                "lnkPath"  : lnk_path,
                "icon"     : _app_icon(name),
                "isRunning": is_running,
            })
        except Exception:
            continue

    # Sort: running first, then alphabetical
    apps.sort(key=lambda a: (not a["isRunning"], a["name"].lower()))
    # Fallback: if start menu scan fails, return common apps
    if not apps:
        fallback_exes = [
            ("Notepad",       "notepad.exe"),
            ("Calculator",    "calc.exe"),
            ("File Explorer", "explorer.exe"),
            ("Task Manager",  "taskmgr.exe"),
            ("Command Prompt","cmd.exe"),
            ("Paint",         "mspaint.exe"),
        ]
        seen = set()
        for name, exe in fallback_exes:
            exe_name = os.path.basename(exe).lower()
            exists = os.path.exists(exe) or os.sep not in exe
            if exists:
                exe_name_lower = exe_name
            apps.append({
                "name":      wk["name"],
                "exePath":   exe.replace("\\", "/"),
                "icon":      wk["icon"],
                "isRunning": exe_name in running_names,
            })
            seen.add(wk["name"].lower())
    apps.sort(key=lambda a: (not a["isRunning"], a["name"].lower()))
    response = jsonify(apps)
    response.headers["Cache-Control"] = "no-store"
    return response


@app.route("/browse/recent")
def browse_recent():
    recent = []
    recent_folder = os.path.join(
        os.environ.get("APPDATA", ""),
        r"Microsoft\Windows\Recent"
    )
    try:
        entries = sorted(
            [e for e in os.scandir(recent_folder) if e.name.endswith(".lnk")],
            key=lambda e: e.stat().st_mtime, reverse=True
        )[:20]
        for entry in entries:
            name = entry.name.replace(".lnk", "")
            ext  = os.path.splitext(name)[1][1:].lower()
            if   ext in ["mp4","mkv","avi","mov","mp3","wav"]: ico = "🎬"
            elif ext in ["pdf","docx","doc","xlsx","pptx"]:    ico = "📄"
            elif ext in ["py","bat","ps1"]:                    ico = "⚙️"
            elif ext in ["jpg","png","jpeg","gif"]:            ico = "🖼"
            elif ext == "":                                    ico = "📁"
            else: ico = "📄"
            recent.append({
                "path":  entry.path.replace(".lnk","").replace("\\","/"),
                "label": name,
                "isApp": ext == "exe",
                "icon":  ico,
            })
    except: pass
    response = jsonify(recent)
    response.headers["Cache-Control"] = "no-store"
    return response


# ═══════════════════════════════════════════════════════
#  INPUT ENDPOINTS — Mouse / Keyboard
# ═══════════════════════════════════════════════════════

@app.route("/input/mouse/move", methods=["POST"])
def input_mouse_move():
    """Move mouse by delta — SendInput works on lock screen"""
    data = request.get_json()
    dx = int(float(data.get("dx", 0)))
    dy = int(float(data.get("dy", 0)))
    if dx != 0 or dy != 0:
        _move_mouse_relative(dx, dy)
    cx, cy = mouse.position
    return jsonify({"ok": True, "x": cx, "y": cy})


@app.route("/input/mouse/click", methods=["POST"])
def input_mouse_click():
    """Mouse click — SendInput works on lock screen"""
    data   = request.get_json()
    button = data.get("button", "left")
    double = data.get("double", False)
    if button == "right":
        if double:
            _send_mouse_input(MOUSEEVENTF_RIGHTDOWN)
            _send_mouse_input(MOUSEEVENTF_RIGHTUP)
            time.sleep(0.1)
            _send_mouse_input(MOUSEEVENTF_RIGHTDOWN)
            _send_mouse_input(MOUSEEVENTF_RIGHTUP)
        else:
            _send_mouse_input(MOUSEEVENTF_RIGHTDOWN)
            _send_mouse_input(MOUSEEVENTF_RIGHTUP)
    else:
        if double:
            _send_mouse_input(MOUSEEVENTF_LEFTDOWN)
            _send_mouse_input(MOUSEEVENTF_LEFTUP)
            time.sleep(0.08)
            _send_mouse_input(MOUSEEVENTF_LEFTDOWN)
            _send_mouse_input(MOUSEEVENTF_LEFTUP)
        else:
            _send_mouse_input(MOUSEEVENTF_LEFTDOWN)
            _send_mouse_input(MOUSEEVENTF_LEFTUP)
    return jsonify({"ok": True})


@app.route("/input/mouse/scroll", methods=["POST"])
def input_mouse_scroll():
    """Scroll mouse wheel via SendInput"""
    data   = request.get_json()
    amount = int(data.get("amount", 3))
    # WHEEL_DELTA = 120 per notch
    _send_mouse_input(MOUSEEVENTF_WHEEL, data=amount * 120)
    return jsonify({"ok": True})


@app.route("/input/mouse/down", methods=["POST"])
def input_mouse_down():
    """Hold mouse button (for drag) — SendInput"""
    global _drag_active, _drag_button
    data   = request.get_json()
    button = data.get("button", "left")
    _drag_active = True
    if button == "right":
        _drag_button = Button.right
        _send_mouse_input(MOUSEEVENTF_RIGHTDOWN)
    else:
        _drag_button = Button.left
        _send_mouse_input(MOUSEEVENTF_LEFTDOWN)
    return jsonify({"ok": True, "dragging": True})


@app.route("/input/mouse/up", methods=["POST"])
def input_mouse_up():
    """Release held mouse button — SendInput"""
    global _drag_active, _drag_button
    if _drag_active:
        if _drag_button == Button.right:
            _send_mouse_input(MOUSEEVENTF_RIGHTUP)
        else:
            _send_mouse_input(MOUSEEVENTF_LEFTUP)
        _drag_active = False
    return jsonify({"ok": True, "dragging": False})


@app.route("/input/mouse/drag", methods=["POST"])
def input_mouse_drag():
    """Smooth drag from A to B — SendInput"""
    data   = request.get_json()
    fx, fy = data.get("fromX", 0), data.get("fromY", 0)
    tx, ty = data.get("toX",   0), data.get("toY",   0)
    dur    = float(data.get("duration", 0.4))

    _move_mouse_absolute(fx, fy)
    time.sleep(0.05)
    _send_mouse_input(MOUSEEVENTF_LEFTDOWN)
    time.sleep(0.08)

    steps = max(15, int(dur * 60))
    for i in range(1, steps + 1):
        t  = i / steps
        ix = int(fx + (tx - fx) * t)
        iy = int(fy + (ty - fy) * t)
        _move_mouse_absolute(ix, iy)
        time.sleep(dur / steps)

    _send_mouse_input(MOUSEEVENTF_LEFTUP)
    return jsonify({"ok": True})


@app.route("/input/keyboard/key", methods=["POST"])
def input_keyboard_key():
    key = request.get_json().get("value", "")
    try:
        execute_key_press({"type": "KEY_PRESS", "value": key})
        return jsonify({"ok": True})
    except Exception as e:
        log.error(f"Key error: {e}")
        return jsonify({"ok": False, "error": str(e)}), 500


@app.route("/input/keyboard/type", methods=["POST"])
def input_keyboard_type():
    """Type text — uses clipboard paste first, fallback to SendInput char by char"""
    text = request.get_json().get("value", "")
    try:
        # Try clipboard paste (fastest, works when logged in)
        import win32clipboard
        win32clipboard.OpenClipboard()
        win32clipboard.EmptyClipboard()
        win32clipboard.SetClipboardText(text, win32clipboard.CF_UNICODETEXT)
        win32clipboard.CloseClipboard()
        time.sleep(0.1)
        _send_combo(VK["CTRL"], VK["V"])
    except Exception:
        # Fallback: SendInput char by char (works on lock screen)
        _type_string_sendinput(text)
    return jsonify({"ok": True})


# ═══════════════════════════════════════════════════════
#  ★ KEEP-ALIVE / WATCHDOG
#  Prevents agent from being killed by Windows power mgmt
#  Also prevents Flask from timing out idle connections
# ═══════════════════════════════════════════════════════

_start_time = time.time()

def keep_alive_worker():
    """
    Runs forever in background:
    - Pings self every 30s to keep Flask warm
    - Logs heartbeat so you can verify agent is alive
    - Resets Windows idle timer so system doesn't sleep agent
    """
    while True:
        try:
            # Reset Windows display/system idle timer
            ctypes.windll.kernel32.SetThreadExecutionState(
                0x80000000 | 0x00000001 | 0x00000002
                # ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED
            )
            log.info(f"[HEARTBEAT] Agent alive. Uptime: {int(time.time()-_start_time)}s")
        except Exception as e:
            log.warning(f"Keep-alive error: {e}")
        time.sleep(30)


def flask_server_runner():
    """Start Flask. Auto-restart if it crashes."""
    while True:
        try:
            log.info(f"Starting Flask on {HOST}:{PORT}")
            from werkzeug.serving import make_server

            # ── KEY FIX: threaded=True + no request timeout ──
            # This allows many simultaneous connections from phone
            # and never drops idle connections
            server = make_server(
                HOST, PORT, app,
                threaded=True,   # Handle multiple requests simultaneously
            )
            # Increase socket timeout to prevent disconnects
            server.socket.settimeout(None)  # No timeout = never disconnect
            server.serve_forever()
        except Exception as e:
            log.error(f"Flask crashed: {e}. Restarting in 3s...")
            time.sleep(3)


# ═══════════════════════════════════════════════════════
#  SYSTEM TRAY
# ═══════════════════════════════════════════════════════

def run_tray():
    try:
        import pystray
        from PIL import Image, ImageDraw

        img = Image.new("RGB", (64, 64), color=(30, 100, 200))
        draw = ImageDraw.Draw(img)
        draw.rectangle([10, 10, 54, 44], fill=(255, 255, 255))
        draw.rectangle([22, 44, 42, 54], fill=(255, 255, 255))
        draw.rectangle([14, 48, 50, 54], fill=(255, 255, 255))

        def on_quit(icon, item):
            icon.stop()
            os._exit(0)

        def show_ip(icon, item):
            try:
                ip = _get_local_ip()
                ctypes.windll.user32.MessageBoxW(
                    0,
                    f"PC Command Agent v5.0\n\nIP Address: {ip}\nPort: {PORT}\nSecret Key: {SECRET_KEY}\n\nEnter this IP in your Android app.\n\nLock Screen: Run --install for login screen support.",
                    "PC Command Agent",
                    0x40
                )
            except: pass

        icon = pystray.Icon(
            "PCCommandAgent",
            img,
            "PC Command Agent v5.0 — Always On",
            menu=pystray.Menu(
                pystray.MenuItem("Show IP Address", show_ip),
                pystray.MenuItem("Quit Agent", on_quit),
            )
        )
        icon.run()
    except ImportError:
        log.warning("pystray not installed — no tray icon")
        try:
            while True:
                time.sleep(60)
        except KeyboardInterrupt:
            pass


# ═══════════════════════════════════════════════════════
#  ★ AUTO-INSTALL AS WINDOWS TASK SCHEDULER SERVICE
#  Run: python agent_v4.py --install
#  This makes agent start at boot, run on lock screen,
#  and run as SYSTEM (highest privileges)
# ═══════════════════════════════════════════════════════

def install_as_service():
    """
    Install agent as a Task Scheduler task that:
    - Starts at boot (before login)
    - Runs as SYSTEM (works on lock screen)
    - Restarts automatically if it crashes
    - Never stops
    """
    python_exe = sys.executable
    script_path = os.path.abspath(__file__)
    task_name = "PCCommandAgentV4"

    # Build the schtasks XML for maximum reliability
    xml_content = f"""<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>PC Command Agent v5.0 - Remote control via Android app</Description>
  </RegistrationInfo>
  <Triggers>
    <BootTrigger>
      <Enabled>true</Enabled>
    </BootTrigger>
    <LogonTrigger>
      <Enabled>true</Enabled>
    </LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>S-1-5-18</UserId>
      <RunLevel>HighestAvailable</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>false</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Priority>4</Priority>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>999</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>{python_exe}</Command>
      <Arguments>"{script_path}"</Arguments>
      <WorkingDirectory>{os.path.dirname(script_path)}</WorkingDirectory>
    </Exec>
  </Actions>
</Task>"""

    # Save XML to temp file
    xml_path = os.path.join(os.environ.get("TEMP", "C:\\Temp"), "agent_task.xml")
    with open(xml_path, "w", encoding="utf-16") as f:
        f.write(xml_content)

    # Delete old task if exists
    subprocess.run(
        ["schtasks", "/Delete", "/TN", task_name, "/F"],
        capture_output=True
    )

    # Create new task from XML
    result = subprocess.run(
        ["schtasks", "/Create", "/XML", xml_path, "/TN", task_name, "/F"],
        capture_output=True, text=True
    )

    if result.returncode == 0:
        print("=" * 60)
        print("  ✅ PC Command Agent v5.0 installed as service!")
        print("=" * 60)
        print(f"  Task Name: {task_name}")
        print(f"  Script:    {script_path}")
        print(f"  Python:    {python_exe}")
        print()
        print("  The agent will now:")
        print("  • Start automatically at every boot")
        print("  • Run on the Windows login/lock screen")
        print("  • Auto-restart if it crashes (up to 999 times)")
        print("  • Never stop running")
        print()
        print("  Starting agent now...")
        print("=" * 60)

        # Also start it immediately
        subprocess.Popen(
            ["schtasks", "/Run", "/TN", task_name],
            creationflags=subprocess.CREATE_NO_WINDOW
        )
    else:
        print("=" * 60)
        print("  ❌ Installation failed!")
        print(f"  Error: {result.stderr}")
        print()
        print("  Make sure you ran as Administrator:")
        print("  Right-click Command Prompt → Run as administrator")
        print(f"  Then: python \"{script_path}\" --install")
        print("=" * 60)

    os.remove(xml_path)


def uninstall_service():
    result = subprocess.run(
        ["schtasks", "/Delete", "/TN", "PCCommandAgentV4", "/F"],
        capture_output=True, text=True
    )
    if result.returncode == 0:
        print("✅ Agent service removed.")
    else:
        print(f"❌ Failed to remove: {result.stderr}")


def _get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return socket.gethostbyname(socket.gethostname())


# ═══════════════════════════════════════════════════════
#  MAIN ENTRY POINT
# ═══════════════════════════════════════════════════════

if __name__ == "__main__":

    # ── Handle command-line arguments ──────────────────
    if "--install" in sys.argv:
        install_as_service()
        sys.exit(0)

    if "--uninstall" in sys.argv:
        uninstall_service()
        sys.exit(0)

    # ── Hide console window ────────────────────────────
    try:
        hwnd = ctypes.windll.kernel32.GetConsoleWindow()
        if hwnd:
            ctypes.windll.user32.ShowWindow(hwnd, 0)
    except Exception:
        pass

    local_ip = _get_local_ip()

    # ── Write startup info to log ──────────────────────
    log.info("=" * 50)
    log.info("  PC Command Agent v5.0 STARTING")
    log.info("=" * 50)
    log.info(f"  IP Address : {local_ip}")
    log.info(f"  Port       : {PORT}")
    log.info(f"  Secret Key : {SECRET_KEY}")
    log.info(f"  Log file   : {LOG_PATH}")
    log.info("=" * 50)
    log.info("  New in v4:")
    log.info("  • /wakescreen  - wake PC from phone")
    log.info("  • /unlock      - type password from phone")
    log.info("  • Never stops  - watchdog + auto-restart")
    log.info("  • No timeouts  - connect anytime, any duration")
    log.info("=" * 50)

    # ── Start keep-alive thread ────────────────────────
    threading.Thread(target=keep_alive_worker, daemon=True).start()

    # ── Start Flask server (auto-restarts on crash) ────
    flask_thread = threading.Thread(target=flask_server_runner, daemon=True)
    flask_thread.start()

    # ── Run system tray (blocks main thread) ──────────
    try:
        import pystray
        run_tray()
    except ImportError:
        # No pystray — show console briefly then keep running
        try:
            hwnd = ctypes.windll.kernel32.GetConsoleWindow()
            if hwnd:
                ctypes.windll.user32.ShowWindow(hwnd, 1)
        except: pass

        print("=" * 56)
        print("  PC Command Agent v5.0 — ALWAYS ON")
        print("=" * 56)
        print(f"  IP Address: {local_ip}")
        print(f"  Port:       {PORT}")
        print(f"  Secret Key: {SECRET_KEY}")
        print(f"  Log:        {LOG_PATH}")
        print("=" * 56)
        print("  Install tray icon: pip install pystray pillow")
        print("  Install as service: python agent_v4.py --install")
        print("  Press Ctrl+C to stop.")
        print("=" * 56)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            log.info("Agent stopped by user.")


# ═══════════════════════════════════════════════════════
#  HOW TO USE FROM YOUR ANDROID APP
#
#  1. WAKE PC FROM PHONE (when screen is off/locked):
#     GET  http://<PC_IP>:5000/wakescreen
#     Header: X-Secret-Key: my_secret_123
#
#  2. TYPE PASSWORD + LOGIN (lock screen):
#     POST http://<PC_IP>:5000/unlock
#     Header: X-Secret-Key: my_secret_123
#     Body:   {"password": "YourWindowsPassword"}
#
#  3. Or manually (step by step):
#     POST /wakescreen                          ← wake screen
#     POST /input/keyboard/type  {"value":"YourPassword"}
#     POST /input/keyboard/key   {"value":"ENTER"}
#
#  4. Check if agent is online:
#     GET  http://<PC_IP>:5000/ping
#     (no auth needed)
#
#  ── INSTALL AS SERVICE (lock screen support) ────────
#  Run as Administrator in Command Prompt:
#     python agent_v4.py --install
#
#  This makes the agent:
#  • Start at every boot (before login screen)
#  • Work on Windows lock/login screen
#  • Auto-restart if it ever crashes
#  • Never require you to manually start it
#
#  ── REMOVE SERVICE ──────────────────────────────────
#     python agent_v4.py --uninstall
# ═══════════════════════════════════════════════════════
