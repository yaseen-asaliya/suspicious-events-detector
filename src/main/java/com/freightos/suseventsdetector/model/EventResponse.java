package com.freightos.suseventsdetector.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;


@Setter
@Getter
@Entity
@ToString(callSuper = true)
@Table(name="event")
public class EventResponse {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Integer id;

	protected Date timestamp;

	private String severity;

	private String email;

	private String ip;

	private String domain;

	private String uri;

	private String message;

	private String country;

	private String sessionUUID;

	private String requestHeaders;

	private String responseHeaders;

}
