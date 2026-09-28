package me.demro.dprefixes.gigachat;

import org.jetbrains.annotations.NotNull;

/**
 * Исключение при работе с GigaChat API.
 */
public class GigaChatException extends Exception {

    public GigaChatException(@NotNull String message) { super(message); }

    public GigaChatException(@NotNull String message, @NotNull Throwable cause) { super(message, cause); }
}