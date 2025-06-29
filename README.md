# Flask Application with Docker Support

A simple Flask application that runs in a Docker container.

## Features
- Clean and professional design
- Responsive layout for all devices
- Interactive skills badges
- Modern card-based layout
- Bootstrap 5 integration
- Font Awesome icons
- Easy to maintain and update

## Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the application:
```bash
python app.py
```

The website will be available at http://localhost:5000

## Structure
- `/templates` - Contains HTML templates
- `/static/css` - Contains CSS styles
- `app.py` - Main Flask application
- `requirements.txt` - Python dependencies

## Customization
- Update content in `templates/index.html`
- Modify styles in `static/css/style.css`
- Add new sections as needed

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
