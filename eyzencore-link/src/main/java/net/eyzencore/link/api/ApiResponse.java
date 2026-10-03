package net.eyzencore.link.api;

public record ApiResponse(int statusCode, String body, boolean ok) {

    public boolean unauthorized() {
        return statusCode == 401;
    }

    public boolean clientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean serverError() {
        return statusCode >= 500 || statusCode == 0;
    }
}
