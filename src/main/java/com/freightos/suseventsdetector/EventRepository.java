package com.freightos.suseventsdetector;

import java.util.ArrayList;

import com.freightos.suseventsdetector.model.EventResponse;
import com.freightos.suseventsdetector.model.UnauthorizedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends CrudRepository<EventResponse, Integer> {

    ArrayList<EventResponse> findAllByEmail(String email);

    ArrayList<EventResponse> findAllByIp(String ip);

    @Query("select new com.freightos.suseventsdetector.model.UnauthorizedUser(date(e.timestamp), e.email, count(e.email)) from EventResponse e where e.responseHeaders like %?1% GROUP BY DATE(e.timestamp), e.email having count(e.email) > CAST( ?2 as long) ORDER BY DATE(e.timestamp), e.email")
    ArrayList<UnauthorizedUser> findAllUnAuthorized (String queryString, int threshold);

}
