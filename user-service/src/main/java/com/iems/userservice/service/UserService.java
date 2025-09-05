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

    public Optional<UserResponseDto> updateUser(UUID id, UserRequestDto userRequest) {
        return repository.findById(id).map(existing -> {
            applyUpdates(existing, userRequest);
            return convertToUserResponse(repository.save(existing));
        });
    }

    public Optional<UserResponseDto> updateMyProfile(UUID id, UserRequestDto userRequest) {
        return repository.findById(id).map(existing -> {
            applySelfProfileUpdates(existing, userRequest);
            return convertToUserResponse(repository.save(existing));
        });
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
        user.setBankAccountNumber(userRequest.getBankAccountNumber());
        user.setBankName(userRequest.getBankName());
        user.setContractType(userRequest.getContractType());
        user.setStartDate(userRequest.getStartDate());

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
                user.getBankAccountNumber(),
                user.getBankName(),
                user.getContractType(),
                user.getStartDate(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void applyUpdates(User user, UserRequestDto userRequest) {
        if (userRequest.getFirstName() != null) user.setFirstName(userRequest.getFirstName());
        if (userRequest.getLastName() != null) user.setLastName(userRequest.getLastName());
        if (userRequest.getEmail() != null) user.setEmail(userRequest.getEmail());
        if (userRequest.getAddress() != null) user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null) user.setPhone(userRequest.getPhone());
        if (userRequest.getDob() != null) user.setDob(userRequest.getDob());
        if (userRequest.getGender() != null) user.setGender(userRequest.getGender());
        if (userRequest.getPersonalID() != null) user.setPersonalID(userRequest.getPersonalID());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());
        if (userRequest.getBankAccountNumber() != null) user.setBankAccountNumber(userRequest.getBankAccountNumber());
        if (userRequest.getBankName() != null) user.setBankName(userRequest.getBankName());
        if (userRequest.getContractType() != null) user.setContractType(userRequest.getContractType());
        if (userRequest.getStartDate() != null) user.setStartDate(userRequest.getStartDate());
    }

    private void applySelfProfileUpdates(User user, UserRequestDto userRequest) {
        if (userRequest.getAddress() != null) user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null) user.setPhone(userRequest.getPhone());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());
        if (userRequest.getBankAccountNumber() != null) user.setBankAccountNumber(userRequest.getBankAccountNumber());
        if (userRequest.getBankName() != null) user.setBankName(userRequest.getBankName());
        // Intentionally ignore: firstName, lastName, email, dob, gender, personalID, contractType, startDate
    }

}