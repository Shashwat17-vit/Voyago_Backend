package backend.voyago.SpringBackend.exception;

/**
 * Thrown when the caller is authenticated but not allowed to touch this trip —
 * not a member, not the admin, or the trip is CONFIRMED and therefore locked.
 * Controllers map this to HTTP 403 instead of the usual 400.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
