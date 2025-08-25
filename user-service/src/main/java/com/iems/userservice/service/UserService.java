package com.iems.userservice.service;

import com.iems.userservice.dto.request.UserRequestDto;
import com.iems.userservice.dto.response.UserResponseDto;
import com.iems.userservice.entity.User;
import com.iems.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository repository;

    public UserResponseDto saveUser(UserRequestDto userRequest) {
        return convertToUserResponse(repository.save(convertToUser(userRequest)));
    }

    public List<UserResponseDto> getAllUsers() {
        return repository.findAll()
                .stream()
                .map(this::convertToUserResponse)
                .toList();
    }

    public Optional<UserResponseDto> getUserById(UUID id) {
        return repository.findById(id)
                .map(this::convertToUserResponse);
    }
    public void deleteUser(UUID id) {repository.deleteById(id);}

    public User convertToUser(UserRequestDto userRequest) {
        if (userRequest == null) {
            return null;
        }

        User user = new User();
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setEmail(userRequest.getEmail());
        user.setAddress(userRequest.getAddress());
        user.setPhone(userRequest.getPhone());
        user.setDob(userRequest.getDob());
        user.setGender(userRequest.getGender());
        user.setPersonalID(userRequest.getPersonalID());
        user.setImage(userRequest.getImage());

        return user;
    }
    public UserResponseDto convertToUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponseDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getPhone(),
                user.getDob(),
                user.getGender(),
                user.getPersonalID(),
                user.getImage(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

}