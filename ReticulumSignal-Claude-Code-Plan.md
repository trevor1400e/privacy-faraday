# ReticulumSignal Messenger — Claude Code Build Plan

## Project Overview

Build a native Android (Kotlin, Jetpack Compose) encrypted messaging app that uses:
- **LibSignal** (`org.signal:libsignal-android`) for application-layer E2E encryption (Signal Protocol / Double Ratchet)
- **Reticulum + LXMF** for serverless, metadata-resistant transport (runs as embedded Python via Chaquopy)
- A **Signal-like UI** built in Jetpack Compose

**Critical architecture decision:** Reticulum is a Python library with no Kotlin/JVM port. We use **Chaquopy** (v17.0.0) to embed a Python interpreter inside the Android Studio Gradle project. The Kotlin UI layer calls Python functions via Chaquopy's bridge API, and Python calls back into Kotlin for UI updates. LibSignal is native JVM/Android and runs directly in Kotlin.

**Development philosophy:** Every layer is built and tested independently before integration. The dev/test UI has explicit buttons and status displays to confirm each subsystem works. No blind trust — verify at every step.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────┐
│              Jetpack Compose UI             │
│         (Signal-like chat interface)         │
├─────────────────────────────────────────────┤
│            Kotlin Application Layer          │
│  ┌─────────────────┐  ┌──────────────────┐  │
│  │   LibSignal      │  │  Chaquopy Bridge │  │
│  │   (E2E Encrypt)  │  │  (Kotlin↔Python) │  │
│  │                  │  │                  │  │
│  │  - Key generation│  │  - Start/stop RNS│  │
│  │  - Session mgmt  │  │  - Send LXMF msg │  │
│  │  - Encrypt/Decrypt│ │  - Receive msgs  │  │
│  │  - Identity store│  │  - Peer discovery│  │
│  └─────────────────┘  └──────────────────┘  │
├─────────────────────────────────────────────┤
│          Python Layer (via Chaquopy)         │
│  ┌─────────────────────────────────────────┐│
│  │  reticulum_bridge.py                    ││
│  │  - RNS initialization & config          ││
│  │  - LXMF Router setup                   ││
│  │  - Message send/receive handlers        ││
│  │  - Announce & discovery                 ││
│  │  - Propagation node connection          ││
│  └─────────────────────────────────────────┘│
├─────────────────────────────────────────────┤
│        Reticulum Network Stack (RNS)        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌───────────┐ │
│  │ WiFi │ │LoRa  │ │ I2P  │ │ TCP/UDP   │ │
│  │      │ │      │ │      │ │ (Internet)│ │
│  └──────┘ └──────┘ └──────┘ └───────────┘ │
└─────────────────────────────────────────────┘
```

---

## Data Flow: Sending a Message

```
1. User types message in Compose UI
2. Kotlin: Retrieve Signal session for recipient
3. Kotlin: Encrypt plaintext with libsignal → ciphertext blob
4. Kotlin→Python (Chaquopy): Pass ciphertext + recipient RNS address to reticulum_bridge.py
5. Python: Wrap ciphertext in LXMF message (content field = raw encrypted bytes)
6. Python: LXMF Router delivers over Reticulum (direct link or via propagation node)
7. Recipient Python: LXMF callback fires with incoming message
8. Python→Kotlin (callback): Pass ciphertext + sender RNS address to Kotlin
9. Kotlin: Look up Signal session for sender
10. Kotlin: Decrypt ciphertext with libsignal → plaintext
11. Kotlin: Display in Compose UI
```

**Key insight:** Reticulum already provides its own transport encryption (X25519 + AES). LibSignal adds an independent application-layer encryption on top. The LXMF message content field carries opaque encrypted bytes — LXMF/Reticulum never see plaintext.

---

## Phase 0: Project Scaffolding

**Goal:** Empty Android Studio project with all dependencies compiling.

### Tasks

1. Create new Android Studio project:
   - Language: Kotlin
   - Min SDK: 26 (Android 8.0)
   - Build system: Gradle (Kotlin DSL)
   - UI: Jetpack Compose (BOM latest stable)

2. Add Chaquopy plugin to Gradle:
   ```kotlin
   // settings.gradle.kts
   pluginManagement {
       repositories {
           mavenCentral()
           google()
           gradlePluginPortal()
       }
   }

   // build.gradle.kts (project level)
   plugins {
       id("com.chaquo.python") version "17.0.0" apply false
   }

   // build.gradle.kts (app level)
   plugins {
       id("com.android.application")
       id("org.jetbrains.kotlin.android")
       id("com.chaquo.python")
   }

   android {
       defaultConfig {
           ndk {
               abiFilters += listOf("arm64-v8a", "x86_64")
           }
       }
   }

   chaquopy {
       defaultConfig {
           pip {
               install("rns")
               install("lxmf")
           }
       }
   }
   ```

3. Add LibSignal dependency:
   ```kotlin
   dependencies {
       implementation("org.signal:libsignal-android:0.86.5")
       implementation("org.signal:libsignal-client:0.86.5")
   }
   ```

4. Add remaining dependencies:
   ```kotlin
   dependencies {
       // Compose BOM
       implementation(platform("androidx.compose:compose-bom:2024.10.00"))
       implementation("androidx.compose.ui:ui")
       implementation("androidx.compose.material3:material3")
       implementation("androidx.compose.ui:ui-tooling-preview")

       // Navigation
       implementation("androidx.navigation:navigation-compose:2.7.7")

       // Room (local message DB)
       implementation("androidx.room:room-runtime:2.6.1")
       implementation("androidx.room:room-ktx:2.6.1")
       kapt("androidx.room:room-compiler:2.6.1")

       // Coroutines
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

       // ViewModel
       implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
   }
   ```

5. Create directory structure:
   ```
   app/
   ├── src/main/
   │   ├── java/com/reticulumsignal/messenger/
   │   │   ├── crypto/          # LibSignal wrappers
   │   │   ├── network/         # Chaquopy bridge to Reticulum
   │   │   ├── data/            # Room DB, repositories
   │   │   ├── ui/              # Compose screens
   │   │   │   ├── debug/       # Dev/test screens
   │   │   │   └── chat/        # Production chat UI
   │   │   └── MainApplication.kt
   │   └── python/
   │       └── reticulum_bridge.py
   ```

6. Create `src/main/python/reticulum_bridge.py` with a hello-world function:
   ```python
   def ping():
       return "Reticulum bridge alive"
   ```

7. In `MainApplication.kt`, verify Chaquopy starts:
   ```kotlin
   import com.chaquo.python.Python
   import com.chaquo.python.android.AndroidPlatform

   class MainApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           if (!Python.isStarted()) {
               Python.start(AndroidPlatform(this))
           }
       }
   }
   ```

### Verification Criteria
- [ ] Project compiles with zero errors
- [ ] App launches on emulator (x86_64) and physical device (arm64-v8a)
- [ ] Chaquopy Python interpreter starts (log "Reticulum bridge alive" from ping())
- [ ] `org.signal.libsignal` classes are importable in Kotlin

---

## Phase 1: LibSignal Integration + Test UI

**Goal:** Generate Signal Protocol keys, perform a local handshake between two in-memory identities, encrypt/decrypt a message. Prove it works with visible UI feedback.

### 1A: Signal Protocol Key Management

Create `crypto/SignalKeyManager.kt`:

```kotlin
// This class wraps libsignal key generation and storage.
// It implements the four required stores:
//   IdentityKeyStore, PreKeyStore, SignedPreKeyStore, SessionStore
//
// For dev/test, use in-memory stores.
// For production, back with Room DB (Phase 4).
```

Implementation tasks:
1. Generate `IdentityKeyPair` using `KeyHelper.generateIdentityKeyPair()`
2. Generate `registrationId` using `KeyHelper.generateRegistrationId(false)`
3. Generate 100 `PreKeyRecord`s using `KeyHelper.generatePreKeys(startId, 100)`
4. Generate `SignedPreKeyRecord` using `KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId)`
5. Implement `InMemoryIdentityKeyStore` — stores identity key pairs and trusted identities
6. Implement `InMemoryPreKeyStore` — stores/removes pre-keys by ID
7. Implement `InMemorySignedPreKeyStore` — stores signed pre-keys
8. Implement `InMemorySessionStore` — stores session records by address

### 1B: Signal Session + Encrypt/Decrypt

Create `crypto/SignalSessionManager.kt`:

```kotlin
// Manages session establishment and message encrypt/decrypt.
// Uses SessionBuilder to create sessions from PreKeyBundles.
// Uses SessionCipher to encrypt/decrypt messages.
```

Implementation tasks:
1. `buildSession(remoteAddress, preKeyBundle)` — creates a Signal session with a remote peer
2. `encrypt(remoteAddress, plaintext: ByteArray): CiphertextMessage` — encrypts using the session
3. `decrypt(remoteAddress, ciphertext: ByteArray): ByteArray` — decrypts using the session
4. Handle both `PreKeySignalMessage` (first message) and `SignalMessage` (subsequent) types

### 1C: Dev/Test UI — Signal Handshake Screen

Create `ui/debug/SignalTestScreen.kt` (Jetpack Compose):

```
┌─────────────────────────────────────┐
│         SIGNAL PROTOCOL TEST        │
├─────────────────────────────────────┤
│                                     │
│  Alice Identity: [fingerprint hex]  │
│  Bob Identity:   [fingerprint hex]  │
│                                     │
│  [🔑 Generate Keys]                │
│  Status: ✅ Keys generated          │
│                                     │
│  [🤝 Perform Handshake]            │
│  Status: ✅ Session established     │
│                                     │
│  Message: [___________________]     │
│  [🔒 Encrypt & Decrypt]            │
│                                     │
│  Plaintext:  "Hello Trevor"         │
│  Ciphertext: "a4f2c8...9b1e"       │
│  Decrypted:  "Hello Trevor"         │
│  Match: ✅ YES                      │
│                                     │
│  [📋 View Session State]           │
│  Ratchet key: [hex]                │
│  Chain index: 1                     │
│  Previous counter: 0               │
│                                     │
└─────────────────────────────────────┘
```

Implementation tasks:
1. "Generate Keys" button: Creates Alice + Bob identities, shows fingerprints
2. "Perform Handshake" button: Alice builds session from Bob's PreKeyBundle and vice versa. Shows success/failure status
3. "Encrypt & Decrypt" button: Alice encrypts a typed message, shows ciphertext hex. Bob decrypts, shows result. Compares plaintext vs decrypted with match indicator
4. "View Session State" button: Dumps current ratchet state (current ratchet key, chain index, etc.) to confirm Double Ratchet is advancing
5. Send multiple messages and watch ratchet state change to confirm forward secrecy is working
6. **Negative test**: Attempt to decrypt with wrong session — should show clear error in UI

### Verification Criteria
- [ ] Keys generate without crash
- [ ] Two identities can establish a session via PreKeyBundle exchange
- [ ] Message encrypts to ciphertext that is NOT the plaintext
- [ ] Message decrypts back to original plaintext exactly
- [ ] Ratchet state advances after each message
- [ ] Decryption with wrong key/session shows a clear, handled error (no crash)

---

## Phase 2: Reticulum/LXMF Integration + Test UI

**Goal:** Start Reticulum Network Stack inside the app, create an LXMF identity, announce on the network, send/receive an LXMF message between two devices (or emulator instances). Prove it works.

### 2A: Python Bridge Module

Create `src/main/python/reticulum_bridge.py`:

```python
"""
Reticulum/LXMF bridge for Android.
Called from Kotlin via Chaquopy.

Key functions:
- init_reticulum(config_path): Start RNS with custom config
- get_lxmf_address(): Return this node's LXMF address (hex)
- send_message(destination_hash, content_bytes): Send raw bytes via LXMF
- set_message_callback(callback): Register Kotlin callback for incoming messages
- announce(): Broadcast this node's identity on the network
- get_status(): Return dict with RNS status info
- shutdown(): Clean shutdown of RNS
"""

import RNS
import LXMF
import time
import threading

class ReticulumBridge:
    def __init__(self):
        self.reticulum = None
        self.lxmf_router = None
        self.local_lxmf_destination = None
        self.identity = None
        self.message_callback = None
        self._running = False

    def init_reticulum(self, config_path):
        """
        Initialize Reticulum with a config directory.
        On Android, use app's private storage for config.
        Creates or loads an existing identity.
        Sets up the LXMF Router with a delivery callback.
        """
        self.reticulum = RNS.Reticulum(config_path)

        # Load or create identity
        identity_path = config_path + "/identity"
        if os.path.exists(identity_path):
            self.identity = RNS.Identity.from_file(identity_path)
        else:
            self.identity = RNS.Identity()
            self.identity.to_file(identity_path)

        self.lxmf_router = LXMF.LXMRouter(
            identity=self.identity,
            storagepath=config_path
        )
        self.local_lxmf_destination = self.lxmf_router.register_delivery_identity(
            self.identity,
            display_name="ReticulumSignal"
        )
        self.lxmf_router.register_delivery_callback(self._on_message_received)
        self._running = True
        return self.get_lxmf_address()

    def get_lxmf_address(self):
        """Return the local LXMF address as a hex string."""
        if self.local_lxmf_destination:
            return RNS.prettyhexrep(
                self.local_lxmf_destination.hash
            )
        return None

    def get_identity_hash(self):
        """Return raw identity hash bytes for Signal Protocol address mapping."""
        if self.local_lxmf_destination:
            return self.local_lxmf_destination.hash.hex()
        return None

    def send_message(self, destination_hash_hex, content_bytes):
        """
        Send raw bytes to a destination via LXMF.
        content_bytes = libsignal-encrypted ciphertext.
        Returns: message hash (hex) for delivery tracking.
        """
        dest_hash = bytes.fromhex(destination_hash_hex)
        dest_identity = RNS.Identity.recall(dest_hash)

        if dest_identity is None:
            # Need to request identity first (path resolution)
            RNS.Transport.request_path(dest_hash)
            # Wait for path resolution (with timeout)
            timeout = 15
            start = time.time()
            while dest_identity is None and time.time() - start < timeout:
                time.sleep(0.5)
                dest_identity = RNS.Identity.recall(dest_hash)
            if dest_identity is None:
                return {"status": "error", "message": "Could not resolve destination"}

        lxmf_dest = RNS.Destination(
            dest_identity,
            RNS.Destination.OUT,
            RNS.Destination.SINGLE,
            "lxmf",
            "delivery"
        )
        lxm = LXMF.LXMessage(
            lxmf_dest,
            self.local_lxmf_destination,
            content_bytes,  # Raw encrypted bytes as content
            title="",       # Empty title — we don't use it
            desired_method=LXMF.LXMessage.DIRECT
        )
        lxm.try_propagation_on_fail = True
        self.lxmf_router.handle_outbound(lxm)
        return {"status": "sent", "hash": lxm.hash.hex()}

    def _on_message_received(self, message):
        """Internal callback when LXMF message arrives."""
        if self.message_callback:
            sender_hash = message.source_hash.hex()
            content = message.content  # Raw bytes (encrypted)
            timestamp = message.timestamp
            self.message_callback(sender_hash, content, timestamp)

    def set_message_callback(self, callback):
        """Register the Kotlin-side callback for incoming messages."""
        self.message_callback = callback

    def announce(self):
        """Announce this identity on the Reticulum network."""
        self.local_lxmf_destination.announce()
        return True

    def get_status(self):
        """Return Reticulum network status for debug UI."""
        status = {
            "running": self._running,
            "address": self.get_lxmf_address(),
            "identity_hash": self.get_identity_hash(),
            "transport_enabled": RNS.Transport.identity is not None,
        }
        return status

    def shutdown(self):
        """Clean shutdown."""
        self._running = False
        if self.lxmf_router:
            self.lxmf_router.exit_handler()
        if self.reticulum:
            self.reticulum.exit_handler()


# Singleton instance
_bridge = ReticulumBridge()

# Module-level functions for Chaquopy access
def init_reticulum(config_path):
    return _bridge.init_reticulum(config_path)

def get_lxmf_address():
    return _bridge.get_lxmf_address()

def get_identity_hash():
    return _bridge.get_identity_hash()

def send_message(dest_hash_hex, content_bytes):
    return _bridge.send_message(dest_hash_hex, content_bytes)

def set_message_callback(callback):
    _bridge.set_message_callback(callback)

def announce():
    return _bridge.announce()

def get_status():
    return _bridge.get_status()

def shutdown():
    _bridge.shutdown()
```

### 2B: Kotlin-side Reticulum Wrapper

Create `network/ReticulumManager.kt`:

```kotlin
// Wraps Chaquopy calls to reticulum_bridge.py.
// Runs all Python calls on a background thread (Dispatchers.IO).
// Exposes Kotlin coroutine-friendly API:
//
//   suspend fun initialize(configPath: String): String  // returns LXMF address
//   suspend fun sendMessage(destHash: String, ciphertext: ByteArray): Result
//   fun onMessageReceived(callback: (senderHash: String, content: ByteArray, timestamp: Double) -> Unit)
//   suspend fun announce()
//   suspend fun getStatus(): Map<String, Any>
//   suspend fun shutdown()
//
// IMPORTANT: Chaquopy Python calls MUST happen on a thread that has
// been initialized with Python.getInstance(). The Python instance is
// thread-safe but each thread needs access to the GIL.
```

### 2C: Reticulum Config for Android

Create a default Reticulum config that ships with the app. For development, use TCP interface to connect to a known Reticulum testnet node:

```python
# Default config for development/testing
# Stored at: {app_internal_storage}/reticulum/config

[reticulum]
  enable_transport = False
  share_instance = Yes
  shared_instance_port = 37428
  instance_control_port = 37429

[interfaces]
  [[Default Interface]]
    type = AutoInterface
    enabled = Yes

  # For testing over the internet when devices aren't on same LAN:
  [[TCP Testnet]]
    type = TCPClientInterface
    enabled = No
    target_host = amsterdam.connect.reticulum.network
    target_port = 4965
```

### 2D: Dev/Test UI — Reticulum Test Screen

Create `ui/debug/ReticulumTestScreen.kt`:

```
┌─────────────────────────────────────┐
│       RETICULUM NETWORK TEST        │
├─────────────────────────────────────┤
│                                     │
│  [🚀 Start Reticulum]              │
│  Status: ✅ Running                 │
│                                     │
│  LXMF Address:                      │
│  [a4f2c8d1e9b3...7f2a]             │
│  [📋 Copy]                         │
│                                     │
│  Identity Hash:                     │
│  [9c3b1a8e...f4d2]                 │
│                                     │
│  [📡 Announce on Network]          │
│  Status: ✅ Announced               │
│                                     │
│  ─── Send Test Message ───          │
│  Dest Hash: [_________________]     │
│  Payload:   [_________________]     │
│  [📤 Send Raw LXMF Message]        │
│  Status: ✅ Sent (hash: ab12...)    │
│                                     │
│  ─── Received Messages ───          │
│  [timestamp] from [hash]: [bytes]   │
│  [timestamp] from [hash]: [bytes]   │
│                                     │
│  ─── Network Status ───             │
│  Transport: active                  │
│  Known peers: 3                     │
│  Interface: AutoInterface (WiFi)    │
│                                     │
└─────────────────────────────────────┘
```

Implementation tasks:
1. "Start Reticulum" button: Calls `init_reticulum()`, shows LXMF address on success
2. "Announce" button: Broadcasts identity, confirms announce sent
3. "Send Raw LXMF Message" button: Sends plaintext bytes (NOT encrypted yet — Phase 2 is raw transport test) to a manually entered destination hash
4. Incoming message list: Shows all received LXMF messages in real-time with sender hash, timestamp, and raw payload bytes
5. Network status panel: Shows live Reticulum state

### 2E: Two-Device Test Procedure

Document this test procedure in the app's debug screen:

```
TEST: Two-Device LXMF Messaging
1. Install app on Device A and Device B (or two emulators on same LAN)
2. On both devices: tap "Start Reticulum" → note LXMF addresses
3. On both devices: tap "Announce"
4. On Device A: enter Device B's LXMF address, type "hello from A", tap Send
5. On Device B: confirm message appears in received list with correct payload
6. On Device B: enter Device A's address, type "hello from B", tap Send
7. On Device A: confirm message appears
8. PASS if both messages delivered with correct content
```

### Verification Criteria
- [ ] Reticulum initializes without crash on Android
- [ ] LXMF address is generated and displayed
- [ ] Announce broadcasts without error
- [ ] Two devices on the same WiFi can exchange LXMF messages
- [ ] Messages arrive with correct content bytes
- [ ] App handles Reticulum errors gracefully (no crash on network failure)
- [ ] Python bridge survives app backgrounding and foregrounding

---

## Phase 3: Integration — LibSignal Encryption over Reticulum

**Goal:** Combine Phase 1 and Phase 2. Messages are encrypted with LibSignal before being sent over LXMF/Reticulum. This is the core protocol working end-to-end.

### 3A: Key Exchange Protocol over LXMF

Design the key exchange flow:

```
INITIAL KEY EXCHANGE:
1. Alice and Bob each have:
   - A Reticulum/LXMF identity (generated in Phase 2)
   - A LibSignal identity (generated in Phase 1)
2. Alice wants to message Bob for the first time.
3. Alice sends a KEY_EXCHANGE LXMF message to Bob containing:
   - Her LibSignal IdentityKey (public)
   - Her SignedPreKey (public + signature)
   - One of her PreKeys (public)
   - Her registration ID
   (This is essentially a PreKeyBundle sent over LXMF)
4. Bob receives it, builds a Signal session from Alice's PreKeyBundle.
5. Bob replies with his own PreKeyBundle in a KEY_EXCHANGE message.
6. Alice builds a Signal session from Bob's PreKeyBundle.
7. Both sides now have Signal sessions. Future messages are encrypted.

MESSAGE FORMAT (LXMF content field):
{
  "type": "key_exchange" | "encrypted_message" | "key_exchange_ack",
  "payload": <base64 encoded bytes>,
  "signal_msg_type": "prekey" | "signal" | null
}

Serialized as msgpack (compact, binary-safe) — NOT JSON.
```

### 3B: Message Protocol Layer

Create `crypto/MessageProtocol.kt`:

```kotlin
// Handles serialization of protocol messages.
// Message types:
//   KEY_EXCHANGE (0x01): Contains PreKeyBundle data
//   ENCRYPTED_MESSAGE (0x02): Contains Signal-encrypted ciphertext
//   KEY_EXCHANGE_ACK (0x03): Acknowledges key exchange completion
//   DELIVERY_RECEIPT (0x04): Confirms message delivery
//
// Wire format (simple, compact):
//   [1 byte: type] [4 bytes: payload length] [N bytes: payload]
//
// KEY_EXCHANGE payload (serialized with Protocol Buffers or hand-packed):
//   - registrationId (4 bytes, big-endian)
//   - identityKey (33 bytes, compressed EC point)
//   - signedPreKeyId (4 bytes)
//   - signedPreKey (33 bytes)
//   - signedPreKeySignature (64 bytes)
//   - preKeyId (4 bytes)
//   - preKey (33 bytes)
//
// ENCRYPTED_MESSAGE payload:
//   - signalMessageType (1 byte: 0x01=PreKey, 0x02=Signal)
//   - ciphertext (variable length)
```

### 3C: Integrated Message Manager

Create `MessageManager.kt`:

```kotlin
// Orchestrates the full send/receive flow:
//
// SENDING:
// 1. Check if Signal session exists for recipient
//    - If NO: initiate key exchange first, queue the message
//    - If YES: encrypt with libsignal, send via Reticulum
//
// RECEIVING:
// 1. Parse message type from LXMF content
// 2. If KEY_EXCHANGE: build Signal session, send ACK
// 3. If ENCRYPTED_MESSAGE: decrypt with libsignal, deliver to UI
// 4. If KEY_EXCHANGE_ACK: mark key exchange complete, flush queued messages
//
// STATE MACHINE per contact:
//   UNKNOWN → KEY_EXCHANGE_SENT → ESTABLISHED
//   UNKNOWN → KEY_EXCHANGE_RECEIVED → ESTABLISHED
```

### 3D: Dev/Test UI — Integration Test Screen

Create `ui/debug/IntegrationTestScreen.kt`:

```
┌─────────────────────────────────────┐
│      FULL INTEGRATION TEST          │
├─────────────────────────────────────┤
│                                     │
│  Reticulum: ✅ Running              │
│  LXMF Addr: [a4f2c8d1...]          │
│  Signal ID: [fingerprint]           │
│                                     │
│  ─── Key Exchange ───               │
│  Peer LXMF Addr: [____________]    │
│  [🔑 Initiate Key Exchange]        │
│  Status: ✅ Session established     │
│                                     │
│  ─── Encrypted Messaging ───        │
│  Message: [___________________]     │
│  [📤 Send Encrypted Message]       │
│                                     │
│  ─── Message Log ───                │
│  [→] Encrypted → sent over LXMF    │
│  [←] Received LXMF → decrypted:    │
│      "hello from other device"      │
│                                     │
│  ─── Encryption Proof ───           │
│  Raw LXMF bytes: [hex dump]         │
│  Decrypted text:  "hello"           │
│  Signal session ratchet: [state]    │
│                                     │
│  ─── Verification ───               │
│  [🔍 Compare Safety Numbers]       │
│  Your number:  12345 67890 ...      │
│  Their number: 12345 67890 ...      │
│  Match: ✅                          │
│                                     │
└─────────────────────────────────────┘
```

Key test features:
1. Shows both Reticulum address AND Signal fingerprint simultaneously
2. Key exchange button with step-by-step status (sent → received ACK → session established)
3. Message log showing the encryption pipeline: plaintext → ciphertext hex → LXMF send → LXMF receive → ciphertext hex → plaintext
4. "Encryption Proof" section: Display the raw LXMF content alongside the decrypted text to visually confirm encryption is happening
5. Safety number comparison (libsignal's `NumericFingerprintGenerator`) — both devices should show the same number

### Verification Criteria
- [ ] Key exchange completes between two devices over LXMF
- [ ] Signal sessions are established on both sides
- [ ] Encrypted messages are sent and correctly decrypted
- [ ] Raw LXMF content is visibly encrypted (not plaintext)
- [ ] Safety numbers match between devices
- [ ] Messages work after app restart (sessions persisted)
- [ ] Multiple messages show ratchet advancing
- [ ] Error handling: garbled message → clear error, no crash

---

## Phase 4: Signal-Like Chat UI

**Goal:** Replace the dev/test UI with a production-quality messaging interface. Modeled after Signal's Android app.

### 4A: Room Database Schema

```kotlin
// Tables:
//
// contacts
//   - lxmfAddress: String (primary key, hex)
//   - displayName: String
//   - signalFingerprint: String
//   - sessionEstablished: Boolean
//   - lastSeen: Long (timestamp)
//   - avatarUri: String? (local file path)
//
// messages
//   - id: Long (auto-increment)
//   - conversationId: String (LXMF address of other party)
//   - senderAddress: String
//   - content: String (decrypted plaintext)
//   - timestamp: Long
//   - status: Enum (QUEUED, SENT, DELIVERED, READ, FAILED)
//   - type: Enum (TEXT, IMAGE, VIDEO, FILE)
//   - mediaUri: String? (local file path for media)
//   - mediaSize: Long?
//   - replyToId: Long? (for reply threading)
//
// signal_sessions (backing store for libsignal)
//   - address: String (primary key)
//   - deviceId: Int
//   - sessionRecord: ByteArray
//
// signal_prekeys
//   - preKeyId: Int (primary key)
//   - record: ByteArray
//
// signal_signed_prekeys
//   - signedPreKeyId: Int (primary key)
//   - record: ByteArray
//
// signal_identity_keys
//   - address: String (primary key)
//   - identityKey: ByteArray
//   - trusted: Boolean
```

### 4B: Screen Hierarchy

```
MainScreen (NavHost)
├── ConversationListScreen   — List of all conversations (like Signal's main screen)
│   ├── Each row: avatar, name, last message preview, timestamp, unread badge
│   ├── FAB: New conversation
│   └── Long press: archive, delete, mute
├── ChatScreen               — Individual conversation (like Signal's chat view)
│   ├── Top bar: contact name, avatar, call button (disabled for now), info
│   ├── Message bubbles: sent (right, blue), received (left, gray)
│   ├── Timestamps, delivery status indicators
│   ├── Input bar: text field, attach button, send button
│   └── Reply-to preview when replying
├── ContactDetailScreen      — Contact info
│   ├── LXMF address display
│   ├── Safety number verification
│   └── Block/delete options
├── NewConversationScreen    — Enter LXMF address or scan QR
│   ├── Manual address entry
│   └── QR code scanner (LXMF address as QR)
├── SettingsScreen
│   ├── Your identity info (LXMF address, QR code)
│   ├── Display name setting
│   ├── Reticulum network config
│   └── Interface management (WiFi, TCP, future: LoRa)
└── DebugScreen              — Keep the Phase 1-3 test screens accessible
    ├── Signal Test
    ├── Reticulum Test
    └── Integration Test
```

### 4C: UI Design Notes

- Follow Material 3 / Material You theming
- Color scheme: Dark theme default (like Signal dark mode)
- Message bubbles: Rounded corners, slight shadow
- Sent messages: Right-aligned, themed primary color
- Received messages: Left-aligned, surface variant color
- Delivery indicators: Single check (sent), double check (delivered), clock (pending)
- Typing indicators: Not in v1 (would require Reticulum link presence)
- Animations: Slide-in for new messages, subtle fade for status changes

### Verification Criteria
- [ ] Conversation list shows all contacts with last message
- [ ] Chat screen displays messages in correct order with timestamps
- [ ] Messages persist across app restarts
- [ ] New conversation flow works (enter address → key exchange → chat)
- [ ] QR code displays your LXMF address
- [ ] Settings screen allows display name change
- [ ] Debug screens remain accessible

---

## Phase 5: Media Messages (Images, Video, Files)

**Goal:** Send and receive images, videos, and files. Media is encrypted with LibSignal just like text.

### 5A: Media Handling Design

```
MEDIA FLOW:
1. User selects image/video/file from gallery or camera
2. Kotlin: Read media bytes into memory
3. Kotlin: Compress if needed (images > 1MB get JPEG compressed)
4. Kotlin: Encrypt media bytes with libsignal (same as text messages)
5. Kotlin→Python: For large media, chunk into LXMF-compatible sizes
   NOTE: LXMF over direct links supports larger payloads than LXMF
   over propagation nodes. Max ~16KB over LoRa, ~unlimited over TCP.
6. Send chunked/whole encrypted media via LXMF
7. Recipient reassembles chunks, decrypts, saves to local storage

LXMF FIELDS USAGE:
- Use LXMF's built-in "fields" dict for metadata:
  fields["type"] = "image" | "video" | "file"
  fields["filename"] = "photo.jpg"
  fields["mime"] = "image/jpeg"
  fields["size"] = 1048576
  fields["chunk_index"] = 0
  fields["chunk_total"] = 4
- Content field = encrypted media bytes (or chunk)
```

### 5B: Media UI Components

- Image messages: Thumbnail in chat bubble, tap to fullscreen
- Video messages: Thumbnail with play overlay, tap to play
- File messages: Icon + filename + size, tap to open
- Upload progress indicator on sent media
- Download progress for incoming media

### Verification Criteria
- [ ] Images send and display correctly
- [ ] Videos send and play correctly
- [ ] Files send and can be opened
- [ ] Large media (>1MB) handles chunking without corruption
- [ ] Media is visibly encrypted in raw LXMF content
- [ ] Thumbnails generate correctly

---

## Phase 6: Hardening & Production Readiness

### 6A: Persistent Signal Stores
- Replace in-memory stores with Room-backed stores
- Ensure identity keys survive app reinstall (backup mechanism)

### 6B: Background Service
- Android Foreground Service for Reticulum
- Notification when messages arrive while app is backgrounded
- Wake locks for maintaining Reticulum connectivity

### 6C: Error Handling & Recovery
- Retry logic for failed LXMF deliveries
- Session corruption recovery (re-initiate key exchange)
- Network loss handling (queue messages, send when reconnected)
- LXMF propagation node fallback when direct links fail

### 6D: Security Audit Checklist
- [ ] Signal keys stored in Android Keystore (not just Room)
- [ ] No plaintext written to logs
- [ ] Screen security flag (prevent screenshots)
- [ ] Certificate pinning for any TCP interfaces
- [ ] Memory is zeroed after crypto operations where possible

---

## Key Technical Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Chaquopy + RNS incompatibility | Blocking | Test Phase 0 immediately. If `rns` pip install fails in Chaquopy, check if cryptography/cffi wheels are available. Fallback: compile RNS dependencies for Android manually |
| Chaquopy Python package availability | High | `rns` depends on `cryptography` which Chaquopy supports. `lxmf` depends on `rns` + `msgpack`. Verify all deps install in Chaquopy before proceeding past Phase 0 |
| Chaquopy GIL + threading | Medium | Reticulum runs background threads for transport. Chaquopy's GIL handling should support this, but test that callbacks from Python threads correctly reach Kotlin. Use `runOnUiThread` or `Dispatchers.Main` for UI updates |
| LibSignal version compatibility | Medium | Pin to specific version (0.86.5). The official Android AAR includes prebuilt native libs for arm64-v8a and x86_64 |
| LXMF message size limits | Medium | Direct links: ~500KB practical limit. Via propagation: smaller. Implement chunking in Phase 5 |
| Battery drain from Reticulum | Medium | Use Android Foreground Service with partial wake lock. Consider reducing announce frequency. Allow user to configure poll interval |
| App backgrounding kills Python | High | Android may kill background processes. Use Foreground Service with persistent notification. Save Reticulum state on `onPause`, restore on `onResume` |

---

## Development Order Summary

```
Phase 0 → Verify everything compiles, Chaquopy + RNS install works
         ⬇️ STOP if Chaquopy can't install rns/lxmf
Phase 1 → LibSignal standalone (all in-memory, no network)
         ⬇️ STOP if Signal handshake/encrypt/decrypt fails
Phase 2 → Reticulum standalone (no encryption, raw LXMF)
         ⬇️ STOP if LXMF messages can't cross between devices
Phase 3 → Wire them together (encrypted messages over LXMF)
         ⬇️ STOP if key exchange or encrypted delivery fails
Phase 4 → Signal-like UI (production interface over working protocol)
Phase 5 → Media messages (images, video, files)
Phase 6 → Hardening (persistence, background service, security)
```

**Each phase has its own test UI and verification criteria. Do NOT proceed to the next phase until all criteria pass.**

---

## Files To Create (Phase 0 Starter)

```
app/src/main/java/com/reticulumsignal/messenger/
├── MainApplication.kt              # Chaquopy init
├── MainActivity.kt                 # NavHost, debug screen launcher
├── crypto/
│   ├── SignalKeyManager.kt         # Phase 1
│   ├── SignalSessionManager.kt     # Phase 1
│   ├── SignalStores.kt             # In-memory stores (Phase 1), Room stores (Phase 6)
│   └── MessageProtocol.kt         # Phase 3
├── network/
│   ├── ReticulumManager.kt         # Phase 2
│   └── MessageManager.kt          # Phase 3 (orchestrator)
├── data/
│   ├── AppDatabase.kt             # Phase 4
│   ├── entities/                   # Phase 4
│   └── dao/                       # Phase 4
└── ui/
    ├── debug/
    │   ├── SignalTestScreen.kt     # Phase 1
    │   ├── ReticulumTestScreen.kt  # Phase 2
    │   ├── IntegrationTestScreen.kt # Phase 3
    │   └── DebugHomeScreen.kt      # Tab layout for all test screens
    ├── chat/
    │   ├── ConversationListScreen.kt # Phase 4
    │   ├── ChatScreen.kt            # Phase 4
    │   └── components/              # Phase 4 (bubbles, input bar, etc.)
    ├── contacts/
    │   ├── NewConversationScreen.kt  # Phase 4
    │   └── ContactDetailScreen.kt   # Phase 4
    └── settings/
        └── SettingsScreen.kt        # Phase 4

app/src/main/python/
└── reticulum_bridge.py             # Phase 2
```

---

## Notes for Claude Code

- **Always test on physical device** if possible — emulator networking may not support AutoInterface multicast discovery between instances
- For emulator testing in Phase 2, enable the TCP testnet interface in Reticulum config instead of relying on AutoInterface
- The `libsignal-android` AAR includes native `.so` files — make sure `abiFilters` matches between Chaquopy and libsignal
- When calling Python from Kotlin via Chaquopy, byte arrays need conversion: `PyObject.toJava(ByteArray::class.java)`
- Reticulum's `AutoInterface` uses UDP multicast — this works on WiFi but NOT on cellular. For cellular, configure a TCP interface to a Reticulum transport node
- LXMF address format: 16 bytes (128-bit hash), displayed as 32 hex characters
- Signal Protocol addresses: Map LXMF address (hex) to `SignalProtocolAddress(lxmfHex, deviceId=1)` — we use deviceId=1 since each LXMF identity is a single device
