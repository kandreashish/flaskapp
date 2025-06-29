# Flask Application with Docker Support

A simple Flask application that runs in a Docker container.

## Prerequisites

- Python 3.11+
- Docker
- Docker Compose (optional)

## Running the Application

### Without Docker
1. Install dependencies:
```bash
pip install -r requirements.txt
```
2. Run the application:
```bash
python app.py
```

### With Docker
1. Build the Docker image:
```bash
docker build -t flaskapp .
```
2. Run the container:
```bash
docker run -p 5000:5000 flaskapp
```

## Accessing the Application

Once running, you can access the application at:
- Without Docker: http://localhost:5000
- With Docker: http://localhost:5000

## Project Structure
- `app.py`: Main Flask application
- `requirements.txt`: Python dependencies
- `Dockerfile`: Docker configuration
- `.dockerignore`: Files to exclude from Docker build
