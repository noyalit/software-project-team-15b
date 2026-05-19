package com.software_project_team_15b.Ticketmaster.Controller.common;

public class ApiResponse<T> {

    private T data;
    private String error;

    public ApiResponse() {
    }

    public ApiResponse(T data, String error) {
        this.data = data;
        this.error = error;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isFailure() {
        return error != null && !error.isEmpty();
    }

    public boolean isSuccess() {
        return !isFailure();
    }
}
