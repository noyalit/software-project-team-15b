package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

public class Response<T>
{
    public T data;
    public String errorMessage;

    public Response(T data)
    {
        this.data = data;
    }

    public Response(T data, String errorMessage)
    {
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public Response(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
