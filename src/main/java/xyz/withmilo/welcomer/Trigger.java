package xyz.withmilo.welcomer;

import java.util.Objects;

public class Trigger {
    public String text;
    public int delayMs;

    public Trigger(String text, int delayMs) {
        this.text = text;
        this.delayMs = delayMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trigger)) return false;
        Trigger trigger = (Trigger) o;
        return Objects.equals(text, trigger.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}