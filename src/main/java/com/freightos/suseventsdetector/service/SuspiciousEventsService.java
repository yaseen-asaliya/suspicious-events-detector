package com.freightos.suseventsdetector.service;

import com.freightos.suseventsdetector.EventRepository;
import com.freightos.suseventsdetector.model.UnauthorizedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

@Service
public class SuspiciousEventsService {

    @Autowired
    EventRepository eventRepository;

    public ArrayList<UnauthorizedUser> getUnauthorizedRequests(int threshold) {
        return eventRepository.findAllUnAuthorized("\"isAuthorized\": true", threshold);
    }


    public ArrayList<UnauthorizedUser> getUnauthorizedRequests(String queryString, int threshold) {
        return eventRepository.findAllUnAuthorized(queryString, threshold);
    }
}
