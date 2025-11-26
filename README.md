# Time Series Data Patcher 
A high-performance Java/Spring Boot + Swing desktop tool for repairing and restoring time-series CSV datasets—automatically detecting gaps, patching missing timestamps and values, and exporting clean results locally or to AWS S3 using IAM-scoped credentials.

> **Core Problem Domain**
>
> Data correctness and long-term reliability across large operational datasets.
> Data correctness and long-term reliability across large operational datasets.
Engineers frequently work with incomplete or corrupted time-series logs: irregular sampling intervals, missing or broken timestamp sequences, numeric gaps, malformed rows, and domain-specific keywords that must be preserved. These defects break downstream pipelines, distort analytics, and increase debugging time—especially at scale (10k+ rows/sec, 800+ columns, 120-hour windows).

**Solution** 

The purpose of this app is as a fully automated, multithreaded repair pipeline built for performance and safety.
The app ingests large CSVs, identifies structural defects, reconstructs full timestamp sequences, interpolates numeric gaps, preserves operational keywords, and produces column-level statistical summaries. Processing runs in background threads with drag-and-drop ingestion and safe cancellation. Cleaned datasets are saved locally and optionally uploaded to AWS S3 through the AWS SDK v2 with tightly scoped IAM credentials.

<p align="center">
  <img src="https://github.com/user-attachments/assets/7fe05b7b-3bb9-4779-9863-555133548cb9" width="900" />
</p>


# How To Run The App
[ 1 ] **Install Requirements**
1. Java 17+
2. Maven (`mvn -v` to check your version)
3. AWS CLI (optional, but recommended to use access keys directly)
---
[ 2 ] **Clone the Repository**
```bash
git clone https://github.com/KatavinaNguyen/imputation.git
cd imputation
```
---
[ 3 ] **Create your local `.env` file by copying the template**
```bash
cp example.env. env
```
> then fill in your own values
```env
APP_S3_BUCKET_NAME=your-bucket            # name of the S3 bucket you create
APP_S3_REGION=us-east-1                   # or another region
APP_S3_KEY_PREFIX=imputation/cleaned      # folder/prefix in the bucket (optional)

# (optional if you prefer aws configure)
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
```
> [!NOTE]
> the app reads APP_S3_* from .env.
> AWS credentials can come either from AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY in .env or from aws configure.
---
[ 4 ] **Create an S3 Bucket**
> You'll need to have an AWS account. One can be created in the Free Tier at [aws.amazon.com/free](aws.amazon.com/free).

1. Log in to the AWS Management Console.
2. Go to Services → S3.
3. Click Create bucket.
4. Set:
    - **Bucket name**: e.g. `imputation-cleaned-files`
    - **AWS Region**: `us-east-1` (or another, but then set `APP_S3_REGION` to match)
5. Leave **Object Ownership** as “ACLs disabled (recommended)”.
6. Leave **Block all public access** checked.
7. Scroll down and click **Create bucket**.
    - Remember the exact bucket name and region; they must match your `.env`.

---
[ 5 ] **Create an IAM User with S3 Permissions and Get Access Keys**
1. In the AWS console, go to **Services** → **IAM** → **Users**.
2. Click **Add users**.
3. Enter a user name, e.g. `imputation-app-user`.
4. Under **Select AWS credential type**, check **Access key – Programmatic access**.
5. Click **Next** to the permissions step:
    - For simple testing, choose **Attach policies directly** and add **AmazonS3FullAccess**.
    - Later you can replace this with a least-privilege policy (only `s3:PutObject`, `s3:GetObject`, `s3:ListBucket`).
6. Click through **Next** until **Create user**.
7. On the final screen, copy or download:
    - **Access key ID**
    - **Secret access key**
> [!IMPORTANT]  
> This is the only time you will see the secret key.
> 
> You can now either:
> 
> 1) Put them into `.env` as `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, or
> 2) Configure them via `aws configure` in the next step.
---
[ 6 ] **Configure AWS CLI (optional, but recommended)**
```bash
aws configure
```
> Then enter
``` bash
AWS Access Key ID [None]:        <your Access Key ID>
AWS Secret Access Key [None]:    <your Secret Access Key>
Default region name [None]:      us-east-1        # or your bucket’s region
Default output format [None]:    json
```
> The SDK will now be able to pick up credentials from the default AWS profile.

---
[ 7 ] **Build and Run the App**
```bash
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```
> The app window will open.

1. Optionally select a file tag on the name, or leave as is to replace the old file.
2. Drag a CSV file into the drop area (or click Choose File).
3. File cleaning will begin immediately upon drop in/selection of the file.

# Results
<img width="1472" height="740" alt="imputation-image" src="https://github.com/user-attachments/assets/58e00538-03bb-4a8b-a116-a66361005b39" />

[ 1 ] **Cleans + Interpolates the Data**
- Detects the timestamp interval
- Fills missing timestamps
- Interpolates numeric gaps
- Leaves keyword cells (OK, BLOCK, MAINT, SKIP) untouched
---

[ 2 ] **Appends Statistics to the End of the CSV**

- Average, Median, Min, Max, Mode
- NonNumericalDetected flag for each column
---

[ 3 ] **Saves the Cleaned File Locally**

- Output appears in the same folder as the input
- Filename automatically includes your tag or timestamp
---

[ 4 ] **Uploads the Cleaned File to Your S3 Bucket**

- Uses the bucket/region/prefix from your .env
- If successful: status bar shows “Upload complete” + the S3 URL
- If not: status bar shows the error
---

[ 5 ] **UI Status Updates**

- Shows the original filename
- Shows progress while processing
- Allows you to cancel mid-processing
- Shows final result cleanly once finished

# Future Improvements
[ 1 ] **AI-enhanced imputation for complex datasets**

- Integrate ML/AI-driven models to handle irregular patterns, sensor drift, and noisy data.
- Lightweight forecasting, anomaly detection, or learned interpolation models would improve accuracy beyond deterministic methods.

[ 2 ] **Scalable processing for larger workloads**

- Refactor the core pipeline to support streaming or chunked processing.
- This enables efficient handling of multi-GB files, wider tables, and long-duration time series without memory limitations.

[ 3 ] **Broader set of imputation strategies**

- Introduce selectable methods such as spline interpolation, forward/backward fill, and rolling statistical estimates.
- A method selector in the UI lets users match the imputation strategy to the structure of their dataset.

[ 4 ] **Support for additional input and output formats**

- Add pluggable handlers for Excel, JSON, and Parquet to improve compatibility with analytics and data engineering workflows.
- This expands the tool’s usefulness beyond simple CSV pipelines.

[ 5 ] **Session-level data quality reporting**

- Maintain in-session counters summarizing total gaps filled, keyword detections, and completeness improvements across all processed files.
- At the end of the session, generate a compact summary file highlighting overall data quality improvements.
