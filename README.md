# Stock Option Data Scraper / Gamma Exposure Calculator

This is a Java application that runs on a 15-minute schedule and loads data from two public financial websites.

* It gets the current EFFR interest rate used by stock options for calculating certain variables.
* It gets the current day stock option data for a particular (list of) stock symbols or indexes for analyzation.
* It stores the option chain data and gamma exposure analysis information in a PostgreSQL database.
* It runs every 15 minutes starting at 9:30 AM (Eastern) and stops after 4:00 PM.

It will display the information parsed from HTML code on Yahoo Finance web site as it is read and stored.

This is the back-end portion for building a historical snapshot of option data including gamma exposure for reading by a front-end charting application (React UI with charts).

### Note: None of the code used in this project has been produced in any way by AI.
