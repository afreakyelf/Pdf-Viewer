# discord_webhook.py

import json
import os
import requests

def main():
    webhook_url = os.getenv('DISCORD_WEBHOOK_URL')
    release_tag = os.getenv('RELEASE_TAG')
    release_body = os.getenv('RELEASE_BODY')
    release_url = os.getenv('RELEASE_URL')

    # Construct the message content with Markdown
    content = f"Hello @everyone, new release **{release_tag}** is out 🚀 \n{release_body} \n[View Release]({release_url})"
    
    # Prepare the webhook payload
    payload = {
        "content": content
    }
    
    # Send the webhook
    response = requests.post(webhook_url, json=payload)
    response.raise_for_status()  # This will raise an error if the request fails

if __name__ == "__main__":
    main()
