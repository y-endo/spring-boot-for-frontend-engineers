import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;

class OldEndpointRequest {
    Object m = EndpointRequest.toAnyEndpoint();
}
