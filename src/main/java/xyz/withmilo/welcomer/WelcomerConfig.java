package xyz.withmilo.welcomer;

import java.util.LinkedList;
import java.util.List;

public class WelcomerConfig {
    public static final WelcomerConfig INSTANCE = new WelcomerConfig();

    public List<Trigger> triggers = new LinkedList<>();
    public String response = "hi";

    public WelcomerConfig() {
        // Default trigger
        triggers.add(new Trigger("[+]", 0));
    }

    public Trigger findTrigger(String text) {
        for (Trigger t : triggers) {
            if (t.text.equalsIgnoreCase(text)) return t;
        }
        return null;
    }
}