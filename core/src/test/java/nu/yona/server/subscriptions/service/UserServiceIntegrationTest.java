/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class UserServiceIntegrationTestConfiguration
{
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { UserServiceIntegrationTestConfiguration.class })
public class UserServiceIntegrationTest
{
	@MockBean
	private UserRepository mockUserRepository;

	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@MockBean
	private UserAnonymizedRepository mockUserAnonymizedRepository;

	@Autowired
	private UserService service;

	private final String password = "password";
	private User john;

	@Before
	public void setUpForAll()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Before
	public void setUpPerTest()
	{
		JUnitUtil.setUpRepositoryMock(mockUserRepository);
		JUnitUtil.setUpRepositoryMock(mockMessageSourceRepository);
		JUnitUtil.setUpRepositoryMock(mockUserAnonymizedRepository);

		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		repositoriesMap.put(UserAnonymized.class, mockUserAnonymizedRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			john = User.createInstance("John", "Doe", "jd", "+31612345678", "topSecret", Collections.emptySet());
		}

		when(mockUserRepository.findOne(john.getId())).thenReturn(john);
	}

	/*
	 * Test that the "last app opened date" is updated when the app was opened yesterday
	 */
	@Test
	public void postOpenAppEventNextDay()
	{
		john.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate().minusDays(1));
		service.postOpenAppEvent(john.getId());

		assertThat(john.getAppLastOpenedDate(), equalTo(TimeUtil.utcNow().toLocalDate()));

		verify(mockUserRepository, times(1)).save(any(User.class));
	}

	/*
	 * Test that the "last app opened date" is NOT updated twice on the same day
	 */
	@Test
	public void postOpenAppEventSameDay()
	{
		john.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
		service.postOpenAppEvent(john.getId());

		verify(mockUserRepository, times(0)).save(any(User.class));
	}
}
