# FOOTING APP

This project is an Android application designed to track and analyze running activities using GPS data. It provides real-time location tracking, distance calculation, speed monitoring, and detailed post-run statistics, visualized on an interactive map. Built with modern Android development tools and open-source libraries, this app is ideal for runners looking to monitor their performance and review their routes.
Features

Real-Time Tracking: Records your running path using GPS with start, pause, and stop functionality.
Distance and Duration: Calculates total distance (in kilometers) and elapsed time, accounting for pauses.
Speed Analysis: Displays current and average speed (in km/h) during the run.
Interactive Map: Visualizes the running route using OpenStreetMap (OSM) via osmdroid, with zoom and scroll support.
Post-Run Statistics: Shows detailed stats (date, time, distance, duration, and average speed) after each run.
Historical Data: Stores and displays a history of runs, allowing users to review past activities.
Advanced Visualization: Colors the route based on speed (blue for slow, red for fast).
Local Database: Persists run data using Room for offline access.


**Technologies Used**

Android SDK: Core framework for building the application.
Room: SQLite object mapping library for local data persistence.
osmdroid: Open-source library for embedding interactive maps.
Java: Primary programming language for the app.
