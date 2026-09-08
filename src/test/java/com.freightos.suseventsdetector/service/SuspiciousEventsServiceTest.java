package com.freightos.suseventsdetector.service;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightos.suseventsdetector.model.EventResponse;
import com.freightos.suseventsdetector.model.UnauthorizedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SuspiciousEventsServiceTest {

    @Autowired
    private SuspiciousEventsService suspiciousEventsService;

    @Autowired
    private EventsService eventsService;

    @Test
    public void captureUnauthorizedRequests() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<UnauthorizedUser> expectedUnauthorizedResponse = mapper.readValue(new URL("file:src/test/resources/service/unauthorized_users_response.json"), new TypeReference<List<UnauthorizedUser>>(){});
        List<UnauthorizedUser> actualUnauthorizedResponse = suspiciousEventsService.getUnauthorizedRequests("\"isAuthorized\": true", 3);

        assertNotNull(expectedUnauthorizedResponse);
        assertNotNull(actualUnauthorizedResponse);

        assertEquals(mapper.readTree(mapper.writeValueAsString(expectedUnauthorizedResponse)), mapper.readTree(mapper.writeValueAsString(actualUnauthorizedResponse)));
    }

    @Test
    public void defaultQueryDoesNotFlagRepeatedFailedAuthAttempts() {
        // hardcoded to "isAuthorized": true, so isAuthorized:false accounts are invisible to it
        List<UnauthorizedUser> result = suspiciousEventsService.getUnauthorizedRequests(3);

        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("hacker1@hacking.com")));
        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("hackerdemo@hacking.com")));
        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("rawan@testdomain.com")));
    }

    @Test
    public void arbitraryQueryStringCatchesRepeatedFailedAuthAttempts() {
        // catches exactly what the default method misses
        List<UnauthorizedUser> result = suspiciousEventsService.getUnauthorizedRequests("\"isAuthorized\": false", 10);

        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("hacker1@hacking.com") && u.getCount() == 25));
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("hackerdemo@hacking.com") && u.getCount() == 24));
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("rawan@testdomain.com") && u.getCount() == 41));
    }

    @Test
    public void thresholdIsExclusiveAtTheBoundary() {
        // "having count > threshold" - a count equal to the threshold must not be flagged
        List<UnauthorizedUser> atThreshold = suspiciousEventsService.getUnauthorizedRequests(8);
        List<UnauthorizedUser> belowThreshold = suspiciousEventsService.getUnauthorizedRequests(7);

        assertTrue(atThreshold.stream().noneMatch(u -> u.getEmail().equals("rawand@freightos.com")));
        assertTrue(belowThreshold.stream().anyMatch(u -> u.getEmail().equals("rawand@freightos.com") && u.getCount() == 8));
    }

    @Test
    public void singleOccurrenceNeverExceedsAPositiveThreshold() {
        // a lone event should never be reported as "repeated" activity
        List<UnauthorizedUser> result = suspiciousEventsService.getUnauthorizedRequests(1);

        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("amr@freightos.com")));
    }

    @Test
    public void multipleGroupsAreFilteredIndependentlyByTheSameThreshold() {
        // one threshold, mixed results: hackerdemo (24) drops out, hacker1 (25) and rawan (41) clear it
        List<UnauthorizedUser> result = suspiciousEventsService.getUnauthorizedRequests("\"isAuthorized\": false", 24);

        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("hackerdemo@hacking.com")));
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("hacker1@hacking.com") && u.getCount() == 25));
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("rawan@testdomain.com") && u.getCount() == 41));

        int rawanIndex = indexOfEmail(result, "rawan@testdomain.com");
        int hackerIndex = indexOfEmail(result, "hacker1@hacking.com");
        assertTrue(rawanIndex >= 0 && hackerIndex >= 0 && rawanIndex < hackerIndex,
                "rawan@testdomain.com (2017-09-08) should sort before hacker1@hacking.com (2021-11-02)");
    }

    private int indexOfEmail(List<UnauthorizedUser> list, String email) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getEmail().equals(email)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void sameIpAttackingManyAccountsHasNoAggregateDetection() {
        // credential-stuffing IP; grouping by (date, email) can't see "many accounts, one IP"
        ArrayList<EventResponse> hits = eventsService.getEventsByIP("58.245.25.156");
        long distinctEmails = hits.stream().map(EventResponse::getEmail).distinct().count();

        assertTrue(hits.size() >= 60, "expected a large volume of hits from this single IP");
        assertTrue(distinctEmails >= 20, "expected this IP to have targeted many distinct accounts");

        long oneOffVictims = hits.stream()
                .collect(Collectors.groupingBy(EventResponse::getEmail, Collectors.counting()))
                .values().stream()
                .filter(count -> count <= 2)
                .count();
        assertTrue(oneOffVictims >= 15, "most victims of this scan are hit only once or twice each, "
                + "so no per-email threshold groups them into the single-IP incident they actually are");
    }

    @Test
    public void sameEmailAcrossManyIpsAndCountriesHasNoCrossIpDetection() {
        // impossible-travel signature; nothing inspects ip/country per identity
        ArrayList<EventResponse> hits = eventsService.getEventsByEmail("royce20@witting.org");
        long distinctIps = hits.stream().map(EventResponse::getIp).distinct().count();
        long distinctCountries = hits.stream().map(EventResponse::getCountry).distinct().count();

        assertTrue(distinctIps >= 6, "expected this identity to span multiple IPs");
        assertTrue(distinctCountries >= 5, "expected this identity to span multiple countries");
    }

    @Test
    public void repeatedActivitySpreadAcrossManyDaysNeverAccumulates() throws Exception {
        // per-day grouping resets every day, so low-and-slow abuse never accumulates
        ArrayList<EventResponse> hits = eventsService.getEventsByEmail("normaluser@testin.com");
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");

        assertEquals(11, hits.size());

        long maxHitsInASingleDay = hits.stream()
                .collect(Collectors.groupingBy(e -> dayFormat.format(e.getTimestamp()), Collectors.counting()))
                .values().stream()
                .max(Long::compareTo)
                .orElse(0L);
        assertEquals(2, maxHitsInASingleDay, "11 events spread over 10 months should peak at only 2-in-a-day");

        List<UnauthorizedUser> result = suspiciousEventsService.getUnauthorizedRequests("\"isAuthorized\": false", 2);
        assertTrue(result.stream().noneMatch(u -> u.getEmail().equals("normaluser@testin.com")),
                "even the heaviest single day for this identity doesn't clear a threshold of 2");
    }

    @Test
    public void maliciousUriPayloadIsStoredAndReturnedVerbatim() {
        // uri field has zero validation/sanitization - an XSS payload round-trips as-is
        ArrayList<EventResponse> hits = eventsService.getEventsByEmail("royce20@witting.org");
        boolean hasInjectionAttempt = hits.stream()
                .anyMatch(e -> e.getUri() != null && e.getUri().contains("javascript:void(document.cookie"));

        assertTrue(hasInjectionAttempt, "expected the known XSS payload to be present in the seed data");
    }
}