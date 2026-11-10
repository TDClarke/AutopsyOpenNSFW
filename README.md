# AutopsyOpenNSFW

An [Autopsy](https://www.autopsy.com/) ingest module that scores image files with the [Yahoo open_nsfw](https://github.com/yahoo/open_nsfw) Caffe classifier, so examiners can quickly triage NSFW content in a disk image or logical case.

> **Note:** parts of this README marked `TODO` could not be verified from the repository's public page. Please check them against the source before publishing.

## Features

- Runs as a standard Autopsy **ingest module**, so it fits into the normal ingest workflow.
- Classifies image files using the open_nsfw deep-learning model (OpenCV DNN / Caffe).
- Reports results back into the Autopsy case as artifacts you can review, sort and filter. `TODO: confirm artifact type / tag name and score threshold`
- Structure and approach based on the [FaceRadar](https://github.com/blaizekellystrothers/FaceRadar) ingest module.

## Requirements

- [Autopsy](https://www.autopsy.com/download/) `TODO: confirm tested version, e.g. 4.21.0`
- Java matching your Autopsy release (the Java bundled with Autopsy is used at runtime)
- To build from source: [NetBeans IDE](https://netbeans.apache.org/) with the Autopsy platform configured as a NetBeans platform, and Apache Ant

## Repository layout

```
AutopsyOpenNSFW/
├── build.xml                    # Ant build script (NetBeans module)
├── manifest.mf                  # NetBeans module manifest
├── nbproject/                   # NetBeans project configuration
├── release/modules/             # Bundled runtime resources / libraries
├── src/uk/ac/open/OpenNSFW/     # Ingest module source code
└── FaceRadarDetectedFaces.JPG   # Screenshot inherited from FaceRadar
```

## Building

1. Clone the repository:
   ```bash
   git clone https://github.com/TDClarke/AutopsyOpenNSFW.git
   ```
2. Open the project in NetBeans (**File → Open Project**).
3. Make sure the project's platform points at your Autopsy installation / Autopsy source build (**Project Properties → Libraries**).
4. Right-click the project and choose **Create NBM**. The `.nbm` file is written to the project's `build/` directory.

## Installing in Autopsy

1. In Autopsy, go to **Tools → Plugins → Downloaded**.
2. Click **Add Plugins…** and select the `.nbm` file.
3. Click **Install** and restart Autopsy when prompted.

## Usage

1. Create or open a case and add a data source.
2. In the **Configure Ingest Modules** step, enable **OpenNSFW** `TODO: confirm module display name`.
3. Run ingest.
4. Review flagged images in the results tree `TODO: confirm location, e.g. Data Artifacts / Interesting Items`.

## Model

This module relies on the open_nsfw Caffe model released by Yahoo. The model outputs a probability between 0 and 1 that an image is NSFW. `TODO: document where the model files live (release/modules?) and the default threshold`.

## Limitations

- Automated classification produces false positives and false negatives. Results are a triage aid and **must be reviewed by a human examiner**.
- Only image files supported by the underlying image loader are analysed.

## Related projects

- [saForensics](https://github.com/TDClarke/saForensics): standalone post-extraction scripts (including an open_nsfw sorter) for carved or Volatility-dumped files.
- [FaceRadar](https://github.com/blaizekellystrothers/FaceRadar): the Autopsy face-detection module this project is modelled on.
- [yahoo/open_nsfw](https://github.com/yahoo/open_nsfw): the classifier.

## Contributing

Issues and pull requests are welcome.

## License

`TODO: add a LICENSE file and state it here.` Note that the open_nsfw model and any bundled libraries carry their own licenses.

## Acknowledgements

- Yahoo for open_nsfw
- Blaize Kelly Strothers for FaceRadar
- The [Autopsy](https://github.com/sleuthkit/autopsy) / Sleuth Kit developers
