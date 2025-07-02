# Firebase Setup Guide

This guide will help you set up Firebase authentication for the Expense Tracker application.

## Prerequisites

1. A Firebase project set up in the [Firebase Console](https://console.firebase.google.com/)
2. Firebase Admin SDK service account key file (JSON)

## Configuration

### Option 1: Using Environment Variables (Recommended for Production)

1. Set the Firebase credentials as an environment variable:

   ```bash
   export FIREBASE_CREDENTIALS_JSON='{"type": "service_account", ...}'
   ```

   Replace the JSON content with your actual service account key.

### Option 2: Using a Credentials File (For Development)

1. Place your `serviceAccountKey.json` file in the `src/main/resources/` directory.
2. Add the following to your `application.properties` or `application.yml`:

   ```properties
   # For application.properties
   firebase.credentials.path=serviceAccountKey.json
   ```

   or

   ```yaml
   # For application.yml
   firebase:
     credentials:
       path: serviceAccountKey.json
   ```

## Security Considerations

- **Never** commit the `serviceAccountKey.json` file to version control.
- The `.gitignore` file has been updated to exclude `serviceAccountKey.json`.
- For production, use environment variables or a secure secret management system.

## Testing the Setup

1. Start the application
2. The logs should show "Firebase Admin SDK initialized successfully" on startup
3. Test the authentication endpoints using the provided API documentation

## Troubleshooting

- If you see "File not found" errors, ensure the service account file exists in the correct location.
- For "Invalid credential" errors, verify that the service account key is valid and not expired.
- Check the application logs for detailed error messages.

## Additional Resources

- [Firebase Admin SDK Documentation](https://firebase.google.com/docs/admin/setup)
- [Firebase Authentication](https://firebase.google.com/docs/auth)
- [Firebase Security Rules](https://firebase.google.com/docs/rules)
