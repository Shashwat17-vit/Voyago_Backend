package backend.voyago.SpringBackend.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.config.JwtUtil;
import backend.voyago.SpringBackend.dto.LoginRequest;
import backend.voyago.SpringBackend.dto.SignupRequest;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String signup(SignupRequest request)
    {
        Optional<User> existing = userRepository.findByEmail(request.getEmail());

        if (existing.isPresent())
        {
            String existingProvider = existing.get().getProvider();
            if ("google".equalsIgnoreCase(existingProvider) || "github".equalsIgnoreCase(existingProvider))
            {
                throw new RuntimeException("This email is linked to a " + existingProvider + " account. Please log in with " + existingProvider + ".");
            }
            throw new RuntimeException("Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFull_name(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);
        user.setProvider("LOCAL");

        userRepository.save(user);

        return "User registered successfully";
    }

    public String login(LoginRequest request)
    {
        Optional<User> found = userRepository.findByEmail(request.getEmail());
        if (found.isEmpty())
        {
            throw new RuntimeException("Invalid credentials");
        }

        User user = found.get();

        if (!"LOCAL".equalsIgnoreCase(user.getProvider()))
        {
            throw new RuntimeException("Please log in with " + user.getProvider());
        }

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!passwordMatches)
        {
            throw new RuntimeException("Invalid credentials");
        }

        // Step 4: Generate JWT token — signs the user's email into a token the frontend will store as a cookie
        return jwtUtil.generateToken(user.getEmail());
    }
}
