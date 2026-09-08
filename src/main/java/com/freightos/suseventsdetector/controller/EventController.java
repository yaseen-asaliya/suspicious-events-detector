package com.freightos.suseventsdetector.controller;

import java.util.ArrayList;

import com.freightos.suseventsdetector.model.EventResponse;
import com.freightos.suseventsdetector.model.UnauthorizedUser;
import com.freightos.suseventsdetector.service.EventsService;
import com.freightos.suseventsdetector.service.SuspiciousEventsService;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping(path = {"/events"}, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class EventController {

    private final SuspiciousEventsService suspiciousEventsService;
    private final EventsService eventsService;

    public EventController(SuspiciousEventsService suspiciousEventsService, EventsService eventsService) {
        this.suspiciousEventsService = suspiciousEventsService;
        this.eventsService = eventsService;
    }

    @GetMapping(path = "/eventByEmail")
    public @ResponseBody
    ArrayList<EventResponse> getEventsByEmail(@RequestParam String email) {
        return eventsService.getEventsByEmail(email);
    }

    @GetMapping(path = "/eventByIP")
    public @ResponseBody
    ArrayList<EventResponse> getEventsByIP(@RequestParam String ip) {
        return eventsService.getEventsByIP(ip);
    }

    @GetMapping(path = "/allEvents")
    public @ResponseBody
    ArrayList<EventResponse> getAllEvents() {
        return eventsService.getAllEvents();
    }

    @GetMapping(path = "/captureUnauthorizedRequests")
    public @ResponseBody
    ArrayList<UnauthorizedUser> captureUnauthorizedRequests(@RequestParam(defaultValue = "3") int threshold) {
        return suspiciousEventsService.getUnauthorizedRequests(threshold);
    }
}
