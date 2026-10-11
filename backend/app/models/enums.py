"""Centralized status enums for the FIND-MISSING-PEP system.

Using string enums prevents silent typos and documents all valid status values
in a single authoritative location.
"""
from enum import Enum


class ReportStatus(str, Enum):
    """Valid statuses for a Missing Person report."""
    PROCESSING = "PROCESSING"
    ACTIVE = "ACTIVE"
    FOUND = "FOUND"
    CLOSED = "CLOSED"
    REJECTED_NO_FACE = "REJECTED_NO_FACE"


class SightingStatus(str, Enum):
    """Valid statuses for a Sighting record."""
    PENDING = "PENDING"
    CONFIRMED = "CONFIRMED"
    REJECTED = "REJECTED"


class ConfidenceLevel(str, Enum):
    """Sighting confidence levels."""
    POSSIBLE = "POSSIBLE"
    PROBABLE = "PROBABLE"
    CONFIRMED = "CONFIRMED"
    REJECTED = "REJECTED"


class AgentStatus(str, Enum):
    """Edge Agent device statuses."""
    ONLINE = "ONLINE"
    OFFLINE = "OFFLINE"
    ERROR = "ERROR"


class CameraStatus(str, Enum):
    """Camera statuses."""
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    ERROR = "ERROR"


class PhotoProcessingStatus(str, Enum):
    """Photo face processing statuses."""
    PENDING = "PENDING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    NO_FACE = "NO_FACE"


class NotificationType(str, Enum):
    """Notification event types."""
    SIGHTING = "SIGHTING"
    CONFIRMATION = "CONFIRMATION"
    STATUS_CHANGE = "STATUS_CHANGE"
