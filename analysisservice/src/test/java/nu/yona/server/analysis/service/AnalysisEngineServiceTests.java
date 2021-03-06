/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageDestinationDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TimeUtil;

@RunWith(MockitoJUnitRunner.class)
@Ignore // TODO YD-385 The day activities are now not directly saved to a repository, so this test can't work. Any idea how we'll
		// test this?
public class AnalysisEngineServiceTests
{
	private final Map<String, Goal> goalMap = new HashMap<>();

	@Mock
	private ActivityCategoryService mockActivityCategoryService;
	@Mock
	private ActivityCategoryService.FilterService mockActivityCategoryFilterService;
	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private GoalService mockGoalService;
	@Mock
	private MessageService mockMessageService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private ActivityCacheService mockAnalysisEngineCacheService;
	@Mock
	private DayActivityRepository mockDayActivityRepository;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private LockPool<UUID> userAnonymizedSynchronizer;
	@InjectMocks
	private final AnalysisEngineService service = new AnalysisEngineService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private MessageDestinationDto anonMessageDestination;
	private UUID userAnonId;
	private UserAnonymized userAnonEntity;

	private ZoneId userAnonZoneId;

	@Before
	public void setUp()
	{
		LocalDateTime yesterday = TimeUtil.utcNow().minusDays(1);
		gamblingGoal = BudgetGoal.createNoGoInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<>(Arrays.asList("poker", "lotto")),
						new HashSet<>(Arrays.asList("Poker App", "Lotto App")), usString("Descr")));
		newsGoal = BudgetGoal.createNoGoInstance(yesterday, ActivityCategory.createInstance(UUID.randomUUID(), usString("news"),
				false, new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr")));
		gamingGoal = BudgetGoal.createNoGoInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
						new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr")));
		socialGoal = TimeZoneGoal.createInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
						new HashSet<>(Arrays.asList("social")), Collections.emptySet(), usString("Descr")),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("shopping"), false,
						new HashSet<>(Arrays.asList("webshop")), Collections.emptySet(), usString("Descr")),
				1);

		goalMap.put("gambling", gamblingGoal);
		goalMap.put("news", newsGoal);
		goalMap.put("gaming", gamingGoal);
		goalMap.put("social", socialGoal);
		goalMap.put("shopping", shoppingGoal);

		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());

		when(mockActivityCategoryService.getAllActivityCategories()).thenReturn(getAllActivityCategories());
		when(mockActivityCategoryFilterService.getMatchingCategoriesForSmoothwallCategories(anySetOf(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDto>>() {
					@Override
					public Set<ActivityCategoryDto> answer(InvocationOnMock invocation) throws Throwable
					{
						Object[] args = invocation.getArguments();
						@SuppressWarnings("unchecked")
						Set<String> smoothwallCategories = (Set<String>) args[0];
						return getAllActivityCategories()
								.stream().filter(ac -> ac.getSmoothwallCategories().stream()
										.filter(smoothwallCategories::contains).findAny().isPresent())
								.collect(Collectors.toSet());
					}
				});
		when(mockActivityCategoryFilterService.getMatchingCategoriesForApp(any(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDto>>() {
					@Override
					public Set<ActivityCategoryDto> answer(InvocationOnMock invocation) throws Throwable
					{
						Object[] args = invocation.getArguments();
						String application = (String) args[0];
						return getAllActivityCategories().stream().filter(ac -> ac.getApplications().contains(application))
								.collect(Collectors.toSet());
					}
				});

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		UserAnonymizedDto userAnon = UserAnonymizedDto.createInstance(userAnonEntity);
		anonMessageDestination = userAnon.getAnonymousDestination();
		userAnonId = userAnon.getId();
		userAnonZoneId = userAnon.getTimeZone();

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonId)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonId)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamblingGoal.getId())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, newsGoal.getId())).thenReturn(newsGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamingGoal.getId())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, socialGoal.getId())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, shoppingGoal.getId())).thenReturn(shoppingGoal);

		JUnitUtil.setUpRepositoryMock(mockDayActivityRepository);
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	private Set<ActivityCategoryDto> getAllActivityCategories()
	{
		return goalMap.values().stream().map(goal -> ActivityCategoryDto.createInstance(goal.getActivityCategory()))
				.collect(Collectors.toSet());
	}

	/*
	 * Tests the method to get all relevant categories.
	 */
	@Test
	public void getRelevantSmoothwallCategories()
	{
		assertThat(service.getRelevantSmoothwallCategories(),
				containsInAnyOrder("poker", "lotto", "refdag", "bbc", "games", "social", "webshop"));
	}

	/*
	 * Tests that two conflict messages are generated when the conflict interval is passed.
	 */
	@Test
	public void conflictInterval()
	{
		// Normally there is one conflict message sent.
		// Set a short conflict interval such that the conflict messages are not aggregated.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0.001S");
		p.setConflictInterval("PT0.01S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		mockEarlierActivity(gamblingGoal, nowInAmsterdam());

		// Execute the analysis engine service after a period of inactivity longer than the conflict interval.

		try
		{
			Thread.sleep(11L);
		}
		catch (InterruptedException e)
		{

		}

		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test1", Optional.empty()));

		// Verify that there is a new conflict message sent.
		ArgumentCaptor<MessageDestinationDto> messageDestination = ArgumentCaptor.forClass(MessageDestinationDto.class);
		verify(mockMessageService, times(1)).sendMessageAndFlushToDatabase(any(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getId(),
				equalTo(anonMessageDestination.getId()));

		// Restore default properties.
		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a matching category.
	 */
	@Test
	public void messageCreatedOnMatch()
	{
		ZonedDateTime t = nowInAmsterdam();
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there is a day activity update.
		// first the DayActivity is created and then updated with the Activity
		// so (create, update) = 2 times save
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(2)).save(dayActivity.capture());
		Activity activity = dayActivity.getValue().getLastActivity();
		assertThat("Expect right user set to activity", dayActivity.getValue().getUserAnonymized(), equalTo(userAnonEntity));
		assertThat("Expect start time set just about time of analysis", activity.getStartTimeAsZonedDateTime(),
				greaterThanOrEqualTo(t));
		assertThat("Expect end time set just about time of analysis", activity.getEndTimeAsZonedDateTime(),
				greaterThanOrEqualTo(t));
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoal(), equalTo(gamblingGoal));
		// Verify that there is an activity cached
		verify(mockAnalysisEngineCacheService).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()), any());
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDto> messageDestination = ArgumentCaptor.forClass(MessageDestinationDto.class);
		verify(mockMessageService).sendMessageAndFlushToDatabase(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getId(),
				equalTo(anonMessageDestination.getId()));
		assertThat("Expected right related user set to goal conflict message",
				message.getValue().getRelatedUserAnonymizedId().get(), equalTo(userAnonId));
		assertThat("Expected right goal set to goal conflict message", message.getValue().getGoal().getId(),
				equalTo(gamblingGoal.getId()));
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a not matching and a matching category.
	 */
	@Test
	public void messageCreatedOnMatchOneCategoryOfMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("refdag", "lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(gamblingGoal);
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDto> messageDestination = ArgumentCaptor.forClass(MessageDestinationDto.class);
		verify(mockMessageService).sendMessageAndFlushToDatabase(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getId(),
				equalTo(anonMessageDestination.getId()));
		assertThat("Expect right goal set to goal conflict message", message.getValue().getGoal().getId(),
				equalTo(gamblingGoal.getId()));
	}

	/**
	 * Tests that multiple conflict messages are created when analysis service is called with multiple matching categories.
	 */
	@Test
	public void messagesCreatedOnMatchMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto", "games"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there are 2 day activities updated, for both goals.
		// for every goal, first the DayActivity is created and then updated with the Activity
		// so 2 x (create, update) = 4 times save
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(4)).save(dayActivity.capture());
		assertThat("Expect right goals set to activities",
				dayActivity.getAllValues().stream().map(a -> a.getGoal()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal, gamingGoal));
		// Verify that there are 2 activities updated in the cache, for both goals.
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(gamingGoal.getId()), any());
		// Verify that there are 2 conflict messages sent, for both goals.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDto> messageDestination = ArgumentCaptor.forClass(MessageDestinationDto.class);
		verify(mockMessageService, times(2)).sendMessageAndFlushToDatabase(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getId(),
				equalTo(anonMessageDestination.getId()));
		assertThat("Expect right goals set to goal conflict messages",
				message.getAllValues().stream().map(m -> m.getGoal()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal, gamingGoal));
	}

	/**
	 * Tests that a conflict message is updated when analysis service is called with a matching category after a short time.
	 */
	@Test
	public void messageAggregation()
	{
		// Normally there is one conflict message sent.
		// Set update skip window to 0 such that the conflict messages are aggregated immediately.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		ZonedDateTime t = nowInAmsterdam();
		DayActivity dayActivity = mockEarlierActivity(gamblingGoal, t);
		Activity earlierActivityEntity = dayActivity.getLastActivity();

		// Execute the analysis engine service.
		Set<String> conflictCategories1 = new HashSet<>(Arrays.asList("lotto"));
		Set<String> conflictCategories2 = new HashSet<>(Arrays.asList("poker"));
		Set<String> conflictCategoriesNotMatching1 = new HashSet<>(Arrays.asList("refdag"));
		service.analyze(userAnonId,
				new NetworkActivityDto(conflictCategoriesNotMatching1, "http://localhost/test", Optional.empty()));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories1, "http://localhost/test1", Optional.empty()));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories2, "http://localhost/test2", Optional.empty()));
		service.analyze(userAnonId,
				new NetworkActivityDto(conflictCategoriesNotMatching1, "http://localhost/test3", Optional.empty()));
		ZonedDateTime t2 = nowInAmsterdam();
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories2, "http://localhost/test4", Optional.empty()));

		// Verify that the cache is used to check existing activity
		verify(mockAnalysisEngineCacheService, times(3)).fetchLastActivityForUser(userAnonId, gamblingGoal.getId());
		// Verify that there is no new conflict message sent.
		verify(mockMessageService, never()).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
		// Verify that the existing day activity was updated.
		verify(mockDayActivityRepository, times(3)).save(dayActivity);
		// Verify that the existing activity was updated in the cache.
		verify(mockAnalysisEngineCacheService, times(3)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());
		assertThat("Expect start time to remain the same", earlierActivityEntity.getStartTime(), equalTo(t.toLocalDateTime()));
		assertThat("Expect end time updated at the last executed analysis matching the goals",
				earlierActivityEntity.getEndTimeAsZonedDateTime(), greaterThanOrEqualTo(t2));

		// Restore default properties.
		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNoMatch()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("refdag"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there was no attempted activity update.
		verify(mockAnalysisEngineCacheService, never()).fetchLastActivityForUser(eq(userAnonId), any());
		verify(mockDayActivityRepository, never()).save(any(DayActivity.class));
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(any(), any(), any());

		verifyNoMessagesCreated();
	}

	private void verifyNoMessagesCreated()
	{
		// Verify that there is no conflict message sent.
		verify(mockMessageService, never()).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnTimeZoneGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("webshop"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(shoppingGoal);
		verifyNoMessagesCreated();
	}

	private void verifyActivityUpdate(Goal forGoal)
	{
		// Verify that there is an day activity update.
		// first the DayActivity is created and then updated with the Activity
		// so (create, update) = 2 times save
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(2)).save(dayActivity.capture());
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoal(), equalTo(forGoal));
		// Verify that there is an activity update.
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(forGoal.getId()), any());
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNonZeroBudgetGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("social"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(socialGoal);
		verifyNoMessagesCreated();
	}

	@Test
	public void activityOnNewDay()
	{
		ZonedDateTime today = ZonedDateTime.now(userAnonZoneId).truncatedTo(ChronoUnit.DAYS);
		// mock earlier activity at yesterday 23:59:58,
		// add new activity at today 00:00:01
		ZonedDateTime earlierActivityTime = today.minusDays(1).withHour(23).withMinute(59).withSecond(58);

		DayActivity earlierDayActivity = mockEarlierActivity(gamblingGoal, earlierActivityTime);

		ZonedDateTime startTime = today.withHour(0).withMinute(0).withSecond(1);
		ZonedDateTime endTime = today.withHour(0).withMinute(10);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
		// Verify that a new day was saved
		ArgumentCaptor<DayActivity> newDayActivity = ArgumentCaptor.forClass(DayActivity.class);
		// first the DayActivity is created and then updated with the Activity
		// so (create, update) = 2 times save
		verify(mockDayActivityRepository, times(2)).save(newDayActivity.capture());
		assertThat("Expect new day", newDayActivity.getValue(), not(equalTo(earlierDayActivity)));
		assertThat("Expect right date", newDayActivity.getValue().getStartDate(), equalTo(today.toLocalDate()));
		assertThat("Expect activity added", newDayActivity.getValue().getLastActivity(), notNullValue());
		assertThat("Expect matching start time", newDayActivity.getValue().getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect matching end time", newDayActivity.getValue().getLastActivity().getEndTime(),
				equalTo(endTime.toLocalDateTime()));
		// Verify that a new activity was cached
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());
	}

	@Test
	public void appActivityCompletelyPrecedingLastCachedActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime earlierActivityTime = now;

		DayActivity dayActivity = mockEarlierActivity(gamblingGoal, earlierActivityTime);
		Activity earlierActivityEntity = dayActivity.getLastActivity();

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now.minusSeconds(15);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());
		// Verify that the day was updated
		verify(mockDayActivityRepository, times(1)).save(dayActivity);
		// Verify that the activity was not updated in the cache, because the end time is before the existing cached item
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

		assertThat("Expect a new activity added (merge is quite complicated so has not been implemented yet)",
				dayActivity.getLastActivity().getId(), not(equalTo(earlierActivityEntity.getId())));
		assertThat("Expect right activity start time", dayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
	}

	@Test
	public void appActivityPrecedingAndExtendingLastCachedActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime earlierActivityTime = now.minusSeconds(15);

		DayActivity dayActivity = mockEarlierActivity(gamblingGoal, earlierActivityTime);
		Activity earlierActivityEntity = dayActivity.getLastActivity();

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now;
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());
		// Verify that the day was updated
		verify(mockDayActivityRepository, times(1)).save(dayActivity);
		// Verify that the activity was updated in the cache, because the end time is after the existing cached item
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

		assertThat("Expect a new activity added (merge is quite complicated so has not been implemented yet)",
				dayActivity.getLastActivity().getId(), not(equalTo(earlierActivityEntity.getId())));
		assertThat("Expect right activity start time", dayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
	}

	@Test
	public void appActivityPrecedingCachedDayActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime yesterdayTime = now.minusDays(1);

		mockEarlierActivity(gamblingGoal, now);

		ZonedDateTime startTime = yesterdayTime;
		ZonedDateTime endTime = yesterdayTime.plusMinutes(10);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
		// Verify that a database lookup was done for yesterday
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, yesterdayTime.toLocalDate(), gamblingGoal.getId());
		// Verify that yesterday was inserted in the database
		ArgumentCaptor<DayActivity> precedingDayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(2)).save(precedingDayActivity.capture());
		assertThat("Expect right date", precedingDayActivity.getValue().getStartDate(),
				equalTo(yesterdayTime.truncatedTo(ChronoUnit.DAYS).toLocalDate()));
		assertThat("Expect activity added", precedingDayActivity.getValue().getLastActivity(), notNullValue());
		// Verify that the activity preceding the cached activity was not cached!
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(any(), any(), any());
	}

	private DayActivity mockEarlierActivity(Goal forGoal, ZonedDateTime activityTime)
	{
		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, forGoal, activityTime.getZone(),
				activityTime.truncatedTo(ChronoUnit.DAYS).toLocalDate());
		Activity earlierActivityEntity = Activity.createInstance(activityTime.getZone(), activityTime.toLocalDateTime(),
				activityTime.toLocalDateTime());
		dayActivity.addActivity(earlierActivityEntity);
		ActivityDto earlierActivity = ActivityDto.createInstance(earlierActivityEntity);
		when(mockDayActivityRepository.findOne(userAnonId, dayActivity.getStartDate(), forGoal.getId())).thenReturn(dayActivity);
		when(mockAnalysisEngineCacheService.fetchLastActivityForUser(userAnonId, forGoal.getId())).thenReturn(earlierActivity);
		return dayActivity;
	}

	@Test
	public void crossDayActivity()
	{
		ZonedDateTime startTime = ZonedDateTime.now(userAnonZoneId).minusDays(1);
		ZonedDateTime endTime = ZonedDateTime.now(userAnonZoneId);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// for every day, first the DayActivity is created and then updated with the Activity
		// so 2 x (create, update) = 4 times save
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(4)).save(dayActivity.capture());
		verify(mockAnalysisEngineCacheService, times(2)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

		DayActivity firstDay = dayActivity.getAllValues().get(0);
		DayActivity nextDay = dayActivity.getAllValues().get(2);
		assertThat(firstDay.getStartDate(), equalTo(LocalDate.now().minusDays(1)));
		assertThat(nextDay.getStartDate(), equalTo(LocalDate.now()));

		Activity firstPart = firstDay.getLastActivity();
		Activity nextPart = nextDay.getLastActivity();

		assertThat(firstPart.getStartTime(), equalTo(startTime.toLocalDateTime()));
		ZonedDateTime lastMidnight = ZonedDateTime.now(userAnonZoneId).truncatedTo(ChronoUnit.DAYS);
		assertThat(firstPart.getEndTime(), equalTo(lastMidnight.minusSeconds(1).toLocalDateTime()));
		assertThat(nextPart.getStartTime(), equalTo(lastMidnight.toLocalDateTime()));
		assertThat(nextPart.getEndTime(), equalTo(endTime.toLocalDateTime()));
	}

	private AppActivityDto createSingleAppActivity(String app, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		AppActivityDto.Activity[] activities = { new AppActivityDto.Activity(app, startTime, endTime) };
		return new AppActivityDto(nowInAmsterdam(), activities);
	}

	private ZonedDateTime nowInAmsterdam()
	{
		return ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Europe/Amsterdam"));
	}
}
