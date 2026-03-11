"""
Reticulum/LXMF bridge for Android.
Called from Kotlin via Chaquopy.

Phase 2: Full ReticulumBridge with LXMF messaging.
"""

import os
import signal
import time
import threading

# Monkey-patch signal.signal to a no-op before RNS imports it.
# Python's signal module only works on the main thread, but Chaquopy
# calls from Kotlin Dispatchers.IO which is a background thread.
# Android doesn't need signal handlers anyway.
signal.signal = lambda *args, **kwargs: None

_bridge = None


class ReticulumBridge:
    def __init__(self):
        self.reticulum = None
        self.identity = None
        self.lxmf_router = None
        self.lxmf_destination = None
        self.received_messages = []
        self._lock = threading.Lock()
        self._start_time = None
        self._running = False

    def init_reticulum(self, config_dir):
        """Initialize Reticulum Network Stack and LXMF router."""
        try:
            import RNS
            import LXMF

            # Ensure config directory exists
            os.makedirs(config_dir, exist_ok=True)

            # Start Reticulum
            self.reticulum = RNS.Reticulum(configdir=config_dir)
            self._start_time = time.time()
            self._running = True

            # Create or load identity
            identity_path = os.path.join(config_dir, "identity")
            if os.path.exists(identity_path):
                self.identity = RNS.Identity.from_file(identity_path)
            else:
                self.identity = RNS.Identity()
                self.identity.to_file(identity_path)

            # Setup LXMF router
            self.lxmf_router = LXMF.LXMRouter(
                identity=self.identity,
                storagepath=os.path.join(config_dir, "lxmf_storage"),
            )

            self.lxmf_destination = self.lxmf_router.register_delivery_identity(
                self.identity, display_name="Faraday"
            )

            # Set delivery callback
            self.lxmf_router.register_delivery_callback(self._on_message_received)

            return {
                "status": "ok",
                "address": RNS.prettyhexrep(self.lxmf_destination.hash),
                "identity_hash": self.identity.hexhash,
            }

        except Exception as e:
            return {"status": "error", "error": str(e)}

    def get_lxmf_address(self):
        """Return the LXMF address as hex string."""
        try:
            if self.lxmf_destination is None:
                return {"status": "error", "error": "Not initialized"}
            import RNS

            return {
                "status": "ok",
                "address": RNS.prettyhexrep(self.lxmf_destination.hash),
            }
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def get_identity_hash(self):
        """Return the identity hash as hex string."""
        try:
            if self.identity is None:
                return {"status": "error", "error": "Not initialized"}
            return {"status": "ok", "identity_hash": self.identity.hexhash}
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def send_message(self, dest_hash_hex, content_bytes):
        """Send an LXMF message to a destination hash."""
        try:
            if self.lxmf_router is None:
                return {"status": "error", "error": "Not initialized"}

            import RNS
            import LXMF

            # Clean up the destination hash (prettyhexrep adds <> and colons)
            dest_hash_hex = dest_hash_hex.strip().replace(":", "").replace("<", "").replace(">", "")

            # Resolve destination
            dest_hash = bytes.fromhex(dest_hash_hex)
            dest_identity = RNS.Identity.recall(dest_hash)

            if dest_identity is None:
                # Request path and wait for resolution with timeout
                RNS.Transport.request_path(dest_hash)
                timeout = 15
                start = time.time()
                while dest_identity is None and time.time() - start < timeout:
                    time.sleep(0.5)
                    dest_identity = RNS.Identity.recall(dest_hash)
                if dest_identity is None:
                    return {
                        "status": "error",
                        "error": "Could not resolve destination after 15s. Ensure peer has announced.",
                    }

            lxmf_dest = RNS.Destination(
                dest_identity, RNS.Destination.OUT, RNS.Destination.SINGLE, "lxmf", "delivery"
            )

            # Create and send message
            if isinstance(content_bytes, str):
                content_bytes = content_bytes.encode("utf-8")

            msg = LXMF.LXMessage(
                lxmf_dest,
                self.lxmf_destination,
                content_bytes,
                title="",
                desired_method=LXMF.LXMessage.DIRECT,
            )

            self.lxmf_router.handle_outbound(msg)

            return {
                "status": "ok",
                "message": "Message sent",
                "hash": msg.hash.hex() if msg.hash else "unknown",
            }

        except Exception as e:
            return {"status": "error", "error": str(e)}

    def announce(self):
        """Broadcast identity on the network."""
        try:
            if self.lxmf_destination is None:
                return {"status": "error", "error": "Not initialized"}
            self.lxmf_destination.announce()
            return {"status": "ok", "message": "Announce sent"}
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def get_status(self):
        """Return current status of the Reticulum instance."""
        try:
            if not self._running:
                return {"status": "ok", "running": False, "address": "", "uptime": 0}

            import RNS

            uptime = int(time.time() - self._start_time) if self._start_time else 0
            address = ""
            if self.lxmf_destination:
                address = RNS.prettyhexrep(self.lxmf_destination.hash)

            return {
                "status": "ok",
                "running": True,
                "address": address,
                "uptime": uptime,
            }
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def get_pending_messages(self):
        """Return and clear the received message queue. Called via polling from Kotlin."""
        with self._lock:
            messages = list(self.received_messages)
            self.received_messages.clear()
        return {"status": "ok", "messages": messages}

    def _on_message_received(self, message):
        """Callback when an LXMF message is received."""
        try:
            import RNS

            msg_data = {
                "source_hash": RNS.prettyhexrep(message.source_hash),
                "content": message.content.decode("utf-8", errors="replace")
                if message.content
                else "",
                "timestamp": time.time(),
                "hash": message.hash.hex() if message.hash else "unknown",
            }
            with self._lock:
                self.received_messages.append(msg_data)
        except Exception:
            pass

    def shutdown(self):
        """Clean shutdown of Reticulum."""
        try:
            self._running = False
            if self.reticulum:
                self.reticulum.__transport_enabled = False
            return {"status": "ok", "message": "Shutdown complete"}
        except Exception as e:
            return {"status": "error", "error": str(e)}


def _get_bridge():
    global _bridge
    if _bridge is None:
        _bridge = ReticulumBridge()
    return _bridge


def ping():
    """Simple health check to verify the Python bridge is alive."""
    return "Reticulum bridge alive"


def init_reticulum(config_dir):
    return _get_bridge().init_reticulum(config_dir)


def get_lxmf_address():
    return _get_bridge().get_lxmf_address()


def get_identity_hash():
    return _get_bridge().get_identity_hash()


def send_message(dest_hash_hex, content_bytes):
    return _get_bridge().send_message(dest_hash_hex, content_bytes)


def announce():
    return _get_bridge().announce()


def get_status():
    return _get_bridge().get_status()


def get_pending_messages():
    return _get_bridge().get_pending_messages()


def shutdown():
    return _get_bridge().shutdown()
