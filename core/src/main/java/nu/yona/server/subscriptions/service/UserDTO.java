/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("user")
public class UserDTO
{
	public static final String BUDDIES_REL_NAME = "buddies";
	private UUID id;
	private final String firstName;
	private final String lastName;
	private final String emailAddress;
	private final String mobileNumber;
	private final UserPrivateDTO privateData;

	private UserDTO(UUID id, String firstName, String lastName, String nickName, String emailAddress, String mobileNumber,
			UUID namedMessageSourceID, UUID namedMessageDestinationID, UUID anonymousMessageSourceID,
			UUID anonymousMessageDestinationID, Set<String> deviceNames, Set<String> goalNames, Set<UUID> buddyIDs,
			VPNProfileDTO vpnProfile)
	{
		this(id, firstName, lastName, emailAddress, mobileNumber,
				new UserPrivateDTO(nickName, namedMessageSourceID, namedMessageDestinationID, anonymousMessageSourceID,
						anonymousMessageDestinationID, deviceNames, goalNames, buddyIDs, vpnProfile));
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber)
	{
		this(id, firstName, lastName, emailAddress, mobileNumber, null);
	}

	@JsonCreator
	public UserDTO(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("emailAddress") String emailAddress, @JsonProperty("mobileNumber") String mobileNumber,
			@JsonUnwrapped UserPrivateDTO privateData)
	{
		this(null, firstName, lastName, emailAddress, mobileNumber, privateData);
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber,
			UserPrivateDTO privateData)
	{
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.privateData = privateData;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonIgnore
	public void setUserID(UUID id)
	{
		this.id = id;
	}

	public String getFirstName()
	{
		return firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public String getEmailAddress()
	{
		return emailAddress;
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}

	@JsonUnwrapped
	public UserPrivateDTO getPrivateData()
	{
		return privateData;
	}

	User createUserEntity()
	{
		return User.createInstance(firstName, lastName, privateData.getNickName(), emailAddress, mobileNumber,
				privateData.getDeviceNames(), privateData.getGoals());
	}

	User updateUser(User originalUserEntity)
	{
		originalUserEntity.setFirstName(firstName);
		originalUserEntity.setLastName(lastName);
		originalUserEntity.setNickName(privateData.getNickName());
		originalUserEntity.setEmailAddress(emailAddress);
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setDeviceNames(privateData.getDeviceNames());

		return originalUserEntity;
	}

	private static Set<String> getGoalNames(Set<Goal> goals)
	{
		return goals.stream().map(Goal::getName).collect(toSet());
	}

	static UserDTO createInstance(User userEntity)
	{
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(), userEntity.getEmailAddress(),
				userEntity.getMobileNumber());
	}

	static UserDTO createInstanceWithPrivateData(User userEntity)
	{
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(), userEntity.getNickName(),
				userEntity.getEmailAddress(), userEntity.getMobileNumber(), userEntity.getNamedMessageSource().getID(),
				userEntity.getNamedMessageDestination().getID(), userEntity.getAnonymousMessageSource().getID(),
				userEntity.getAnonymousMessageSource().getDestination().getID(), userEntity.getDeviceNames(),
				getGoalNames(userEntity.getGoals()), getBuddyIDs(userEntity),
				VPNProfileDTO.createInstance(userEntity.getAnonymized()));
	}

	private static Set<UUID> getBuddyIDs(User userEntity)
	{
		return userEntity.getBuddies().stream().map(b -> b.getID()).collect(Collectors.toSet());
	}
}
