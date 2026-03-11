"""
Reticulum/LXMF bridge for Android.
Called from Kotlin via Chaquopy.

Phase 0: Simple ping function to verify Chaquopy Python is working.
Full implementation comes in Phase 2.
"""


def ping():
    """Simple health check to verify the Python bridge is alive."""
    return "Reticulum bridge alive"
