package com.freightos.suseventsdetector.service;

import com.freightos.suseventsdetector.EventRepository;
import com.freightos.suseventsdetector.model.EventResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EventsService {
    @Autowired
    EventRepository eventRepository;

    public ArrayList<EventResponse> getAllEvents() {
        return (ArrayList<EventResponse>) eventRepository.findAll();
    }

    public ArrayList<EventResponse> getEventsByEmail(String email) {
        return eventRepository.findAllByEmail(email);
    }

    public ArrayList<EventResponse> getEventsByIP(String ip) {
        return eventRepository.findAllByIp(ip);
    }
}
