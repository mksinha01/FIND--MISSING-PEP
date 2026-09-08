"""ONVIF CCTV camera discovery over local network via WS-Discovery."""
import logging
import re
import socket
import urllib.parse
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

# Try importing WSDiscovery
try:
    from wsdiscovery import WSDiscovery
    HAS_WSDISCOVERY = True
except ImportError:
    WSDiscovery = None  # type: ignore
    HAS_WSDISCOVERY = False


@dataclass
class DiscoveredCamera:
    """Represents a CCTV camera endpoint discovered on the local network."""
    ip: str
    port: int = 80
    name: str = "ONVIF Camera"
    xaddrs: List[str] = field(default_factory=list)
    rtsp_url: Optional[str] = None
    manufacturer: Optional[str] = None
    model: Optional[str] = None
    scopes: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ip": self.ip,
            "port": self.port,
            "name": self.name,
            "xaddrs": self.xaddrs,
            "rtsp_url": self.rtsp_url,
            "manufacturer": self.manufacturer,
            "model": self.model,
            "scopes": self.scopes,
        }


class ONVIFDiscovery:
    """
    Auto-discovers ONVIF-compliant CCTV cameras on local subnets.
    
    Uses WS-Discovery multicasting on UDP port 3702 to probe network endpoints,
    extracts Device Service XAddrs, metadata scopes, and candidate RTSP URIs.
    """

    def __init__(self, timeout: float = 3.0):
        self.timeout = timeout

    def discover(self, timeout: Optional[float] = None) -> List[DiscoveredCamera]:
        """
        Broadcast WS-Discovery probes and collect responding camera devices.
        
        Args:
            timeout: Probe search timeout in seconds (defaults to self.timeout).
            
        Returns:
            List of DiscoveredCamera objects found on the local network.
        """
        search_timeout = float(timeout) if timeout is not None else self.timeout
        discovered: List[DiscoveredCamera] = []

        if HAS_WSDISCOVERY and WSDiscovery is not None:
            try:
                wsd = WSDiscovery()
                wsd.start()
                logger.info(f"Broadcasting WS-Discovery probe (timeout={search_timeout}s)...")
                services = wsd.searchServices(timeout=search_timeout)
                wsd.stop()

                for service in services:
                    cam = self._parse_ws_service(service)
                    if cam:
                        discovered.append(cam)

                logger.info(f"WS-Discovery completed. Found {len(discovered)} camera(s).")
                return discovered
            except Exception as e:
                logger.warning(f"WSDiscovery probe encountered an error: {e}. Falling back to UDP probe.")

        # Fallback to direct raw socket multicast probe
        return self._raw_udp_probe(timeout=search_timeout)

    def _parse_ws_service(self, service: Any) -> Optional[DiscoveredCamera]:
        """Parse service object returned by wsdiscovery."""
        try:
            # Extract XAddrs
            xaddrs: List[str] = []
            if hasattr(service, "getXAddrs"):
                raw_addrs = service.getXAddrs()
                xaddrs = [str(a) for a in raw_addrs] if raw_addrs else []
            elif hasattr(service, "xAddrs"):
                xaddrs = [str(a) for a in service.xAddrs]

            if not xaddrs:
                return None

            # Extract scopes
            scopes: List[str] = []
            if hasattr(service, "getScopes"):
                raw_scopes = service.getScopes()
                scopes = [str(s.getValue() if hasattr(s, "getValue") else s) for s in raw_scopes]
            elif hasattr(service, "scopes"):
                scopes = [str(s) for s in service.scopes]

            # Parse primary XAddr
            primary_xaddr = xaddrs[0]
            parsed_url = urllib.parse.urlparse(primary_xaddr)
            ip = parsed_url.hostname or "127.0.0.1"
            port = parsed_url.port or (443 if parsed_url.scheme == "https" else 80)

            # Extract name, manufacturer, and model from scopes
            name = "ONVIF Camera"
            manufacturer = None
            model = None

            for s in scopes:
                s_decoded = urllib.parse.unquote(str(s))
                # Scopes often follow format: onvif://www.onvif.org/name/CameraName
                if "/name/" in s_decoded:
                    name = s_decoded.split("/name/")[-1].strip()
                elif "/hardware/" in s_decoded:
                    model = s_decoded.split("/hardware/")[-1].strip()
                elif "/manufacturer/" in s_decoded:
                    manufacturer = s_decoded.split("/manufacturer/")[-1].strip()
                elif "/type/" in s_decoded and not model:
                    model = s_decoded.split("/type/")[-1].strip()

            # Construct candidate RTSP URL
            candidate_rtsp = f"rtsp://{ip}:554/live/ch0?rtsp_transport=tcp"

            return DiscoveredCamera(
                ip=ip,
                port=port,
                name=name,
                xaddrs=xaddrs,
                rtsp_url=candidate_rtsp,
                manufacturer=manufacturer,
                model=model,
                scopes=scopes,
            )
        except Exception as e:
            logger.debug(f"Failed to parse WS-Discovery service item: {e}")
            return None

    def _raw_udp_probe(self, timeout: float = 3.0) -> List[DiscoveredCamera]:
        """
        Direct UDP socket multicast probe on 239.255.255.250:3702 as resilient fallback.
        """
        probe_xml = (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" '
            'xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" '
            'xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery">'
            '<soap:Header>'
            '<wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>'
            '<wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>'
            '<wsa:MessageID>urn:uuid:0a0a0a0a-0000-0000-0000-000000000000</wsa:MessageID>'
            '</soap:Header>'
            '<soap:Body>'
            '<wsd:Probe>'
            '<wsd:Types xmlns:dn="http://www.onvif.org/ver10/network/wsdl">dn:NetworkVideoTransmitter</wsd:Types>'
            '</wsd:Probe>'
            '</soap:Body>'
            '</soap:Envelope>'
        ).encode("utf-8")

        discovered: List[DiscoveredCamera] = []
        sock = None
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            sock.settimeout(timeout)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

            # Send probe to standard ONVIF WS-Discovery multicast group
            sock.sendto(probe_xml, ("239.255.255.250", 3702))

            seen_ips = set()
            while True:
                try:
                    data, (ip, port) = sock.recvfrom(65535)
                    if ip in seen_ips:
                        continue
                    seen_ips.add(ip)

                    content = data.decode("utf-8", errors="ignore")
                    xaddrs = re.findall(r"<[^>]*XAddrs[^>]*>(.*?)</[^>]*XAddrs>", content)
                    xaddr_list = xaddrs[0].split() if xaddrs else [f"http://{ip}:{port}/onvif/device_service"]

                    discovered.append(
                        DiscoveredCamera(
                            ip=ip,
                            port=port,
                            name=f"Camera ({ip})",
                            xaddrs=xaddr_list,
                            rtsp_url=f"rtsp://{ip}:554/live/ch0?rtsp_transport=tcp",
                            scopes=[],
                        )
                    )
                except socket.timeout:
                    break
        except Exception as e:
            logger.debug(f"Raw UDP discovery probe finished or skipped: {e}")
        finally:
            if sock:
                sock.close()

        return discovered

    def get_camera_stream_uri(
        self,
        xaddr: str,
        username: str = "",
        password: str = "",
    ) -> Optional[str]:
        """
        Query ONVIF Media Service for stream RTSP URI using credentials if available.
        """
        try:
            from onvif import ONVIFCamera  # type: ignore
            parsed = urllib.parse.urlparse(xaddr)
            host = parsed.hostname or "127.0.0.1"
            port = parsed.port or 80

            mycam = ONVIFCamera(host, port, username, password)
            media = mycam.create_media_service()
            profiles = media.GetProfiles()
            if profiles:
                token = profiles[0].token
                stream_setup = {
                    "Stream": "RTP-Unicast",
                    "Transport": {"Protocol": "RTSP"},
                }
                res = media.GetStreamUri({"StreamSetup": stream_setup, "ProfileToken": token})
                if res and hasattr(res, "Uri"):
                    uri = str(res.Uri)
                    if "rtsp_transport=" not in uri:
                        delimiter = "&" if "?" in uri else "?"
                        uri = f"{uri}{delimiter}rtsp_transport=tcp"
                    return uri
        except Exception as e:
            logger.debug(f"Could not retrieve dynamic RTSP URI via ONVIF client: {e}")

        # Fallback to standard URL construction
        parsed = urllib.parse.urlparse(xaddr)
        ip = parsed.hostname or "127.0.0.1"
        auth = f"{username}:{password}@" if username and password else ""
        return f"rtsp://{auth}{ip}:554/live/ch0?rtsp_transport=tcp"
