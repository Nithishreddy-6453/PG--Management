import re

with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

provider_xml = """
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
"""

if 'androidx.startup.InitializationProvider' not in content:
    content = content.replace('</application>', provider_xml + '\n    </application>')

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)
