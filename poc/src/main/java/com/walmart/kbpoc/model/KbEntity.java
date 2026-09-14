package com.walmart.kbpoc.model;

public class KbEntity {
    public String entityType; // dollar_threshold | time_window | required_document
    public String value;
    public String unit;
    public String sourceBlockId;
    public double confidence;
}
