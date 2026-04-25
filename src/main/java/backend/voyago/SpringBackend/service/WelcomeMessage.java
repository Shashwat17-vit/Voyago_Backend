package backend.voyago.SpringBackend.service;

import org.springframework.stereotype.Component;

@Component
public class WelcomeMessage {

    public String getWelcome()
    {
        return "Welcome to Voyago";
    }

}

