package com.example.paymentvolumestreamingdemo.web.dto;

import java.time.Instant;

public record PaymentVolumeResponse(Instant asOf, WindowTotal currentWindow, WindowTotal mostRecentCompletedWindow) {
}
