package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

public class Response<T>
{
    protected final T data;
    protected final String errorMessage;

    public Response(T data)
    {
        this.data = data;
        this.errorMessage = null;
    }

    public Response(String errorMessage)
    {
        this.data = null;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return errorMessage == null && data != null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public T getData() {
        return data;
    }
}
