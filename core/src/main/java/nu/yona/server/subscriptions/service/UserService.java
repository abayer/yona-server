/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.Constants;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;

@Service
public class UserService
{
	/** Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers */
	private static Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	// TODO: Do we need this? Currently unused.
	@Transactional
	public UserDTO getUser(String emailAddress, String mobileNumber)
	{
		if (emailAddress != null && mobileNumber != null)
		{
			throw new IllegalArgumentException("Either emailAddress or mobileNumber must be non-null");
		}
		if (emailAddress == null && mobileNumber == null)
		{
			throw new IllegalArgumentException("One of emailAddress and mobileNumber must be null");
		}

		User userEntity = findUserByEmailAddressOrMobileNumber(emailAddress, mobileNumber);
		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Transactional
	public boolean canAccessPrivateData(UUID id)
	{
		return getEntityByID(id).canAccessPrivateData();
	}

	@Transactional
	public UserDTO getPublicUser(UUID id)
	{
		return UserDTO.createInstance(getEntityByID(id));
	}

	@Transactional
	public UserDTO getPrivateUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getEntityByID(id));
	}

	@Transactional
	public UserDTO addUser(UserDTO userResource)
	{
		validateUserFields(userResource);

		User userEntity = userResource.createUserEntity();
		userEntity = User.getRepository().save(userEntity);

		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO userResource)
	{
		User originalUserEntity = getEntityByID(id);
		UserDTO savedUser;
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// TODO: the password needs to come from the URL, not from the
			// payload
			savedUser = handleUserSignupUponBuddyRequest("TODO", userResource, originalUserEntity);
		}
		else
		{
			savedUser = handleUserUpdate(userResource, originalUserEntity);
		}
		return savedUser;
	}

	static User findUserByEmailAddressOrMobileNumber(String emailAddress, String mobileNumber)
	{
		User userEntity;
		if (emailAddress == null)
		{
			userEntity = User.getRepository().findByMobileNumber(mobileNumber);
			if (userEntity == null)
			{
				throw UserNotFoundException.notFoundByMobileNumber(mobileNumber);
			}
		}
		else
		{
			userEntity = User.getRepository().findByEmailAddress(emailAddress);
			if (userEntity == null)
			{
				throw UserNotFoundException.notFoundByEmailAddress(emailAddress);
			}
		}
		return userEntity;
	}

	private UserDTO handleUserSignupUponBuddyRequest(String password, UserDTO userResource, User originalUserEntity)
	{
		UpdatedEntities updatedEntities = updateUserWithTempPassword(userResource, originalUserEntity);
		return saveUserWithDevicePassword(password, updatedEntities);
	}

	static class UpdatedEntities
	{
		final User userEntity;
		final MessageSource namedMessageSource;
		final MessageSource anonymousMessageSource;

		UpdatedEntities(User userEntity, MessageSource namedMessageSource, MessageSource anonymousMessageSource)
		{
			this.userEntity = userEntity;
			this.namedMessageSource = namedMessageSource;
			this.anonymousMessageSource = anonymousMessageSource;
		}
	}

	private UpdatedEntities updateUserWithTempPassword(UserDTO userDTO, User originalUserEntity)
	{
		// TODO: the password needs to come from the URL, not from the
		// payload
		return CryptoSession.execute(Optional.of("TODO"), () -> canAccessPrivateData(userDTO.getID()), () -> {
			User updatedUserEntity = userDTO.updateUser(originalUserEntity);
			MessageSource touchedNameMessageSource = touchMessageSource(updatedUserEntity.getNamedMessageSource());
			MessageSource touchedAnonymousMessageSource = touchMessageSource(updatedUserEntity.getAnonymousMessageSource());
			return new UpdatedEntities(updatedUserEntity, touchedNameMessageSource, touchedAnonymousMessageSource);
		});
	}

	private MessageSource touchMessageSource(MessageSource messageSource)
	{
		messageSource.touch();
		return messageSource;
	}

	private UserDTO saveUserWithDevicePassword(String password, UpdatedEntities updatedEntities)
	{
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(updatedEntities.userEntity.getID()),
				() -> {
					UserDTO savedUser;
					MessageSource.getRepository().save(updatedEntities.namedMessageSource);
					MessageSource.getRepository().save(updatedEntities.anonymousMessageSource);
					savedUser = UserDTO.createInstanceWithPrivateData(User.getRepository().save(updatedEntities.userEntity));
					return savedUser;
				});
	}

	private UserDTO handleUserUpdate(UserDTO userResource, User originalUserEntity)
	{
		return UserDTO.createInstanceWithPrivateData(User.getRepository().save(userResource.updateUser(originalUserEntity)));
	}

	public void deleteUser(Optional<String> password, UUID id)
	{

		User.getRepository().delete(id);
	}

	private User getEntityByID(UUID id)
	{
		User entity = User.getRepository().findOne(id);
		if (entity == null)
		{
			throw UserNotFoundException.notFoundByID(id);
		}
		return entity;
	}

	private void validateUserFields(UserDTO userResource)
	{
		if (StringUtils.isBlank(userResource.getFirstName()))
		{
			throw new InvalidDataException("error.user.firstname");
		}

		if (StringUtils.isBlank(userResource.getLastName()))
		{
			throw new InvalidDataException("error.user.lastname");
		}

		if (StringUtils.isBlank(userResource.getEmailAddress()))
		{
			throw new InvalidDataException("error.user.email.address");
		}

		if (!EmailValidator.getInstance().isValid(userResource.getEmailAddress()))
		{
			throw new InvalidDataException("error.user.email.address.invalid", userResource.getEmailAddress());
		}

		if (StringUtils.isBlank(userResource.getMobileNumber()))
		{
			throw new InvalidDataException("error.user.mobile.number");
		}

		if (!REGEX_PHONE.matcher(userResource.getMobileNumber()).matches())
		{
			throw new InvalidDataException("error.user.mobile.number.invalid");
		}
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> new YonaException("Missing '" + Constants.PASSWORD_HEADER + "' header"));
	}

	public void addBuddy(UserDTO user, BuddyDTO buddy)
	{
		User userEntity = getEntityByID(user.getID());
		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getID());
		userEntity.addBuddy(buddyEntity);
		User.getRepository().save(userEntity);
	}
}
