package backend.voyago.SpringBackend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.dto.SignupRequest;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepositor, PasswordEncoder passwordEncoder)
    { 
        this.userRepository= userRepositor;
        this.passwordEncoder= passwordEncoder;
    }


    public String signup(SignupRequest request)
    {
        if (userRepository.existsByEmail(request.getEmail()))
        {
            throw new RuntimeException("Email already registered");
        }
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFull_name(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);

        userRepository.save(user);

        return "User registered successfully";

    }
}
