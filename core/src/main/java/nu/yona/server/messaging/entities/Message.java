/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.messaging.service.MessageServiceException;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithID
{
	private UUID relatedUserAnonymizedID;

	private ZonedDateTime creationTime;

	public static MessageRepository getRepository()
	{
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, UUID.class);
	}

	/**
	 * This is the only constructor, to ensure that subclasses don't accidentally omit the ID.
	 * 
	 * @param id The ID of the entity
	 */
	protected Message(UUID id, UUID relatedUserAnonymizedID)
	{
		super(id);

		if (id != null && relatedUserAnonymizedID == null)
		{
			throw MessageServiceException.anonymizedUserIdMustBeSet();
		}

		this.relatedUserAnonymizedID = relatedUserAnonymizedID;
		this.creationTime = ZonedDateTime.now();
	}

	public void encryptMessage(Encryptor encryptor)
	{
		encrypt(encryptor);
	}

	public void decryptMessage(Decryptor decryptor)
	{
		decrypt(decryptor);
	}

	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	public UUID getRelatedUserAnonymizedID()
	{
		return relatedUserAnonymizedID;
	}

	protected abstract void encrypt(Encryptor encryptor);

	protected abstract void decrypt(Decryptor decryptor);
}
