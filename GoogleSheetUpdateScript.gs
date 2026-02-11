/**
 * SETTINGS
 * 1. Replace the ID below with your actual Google Sheet ID from the URL.
 * 2. Ensure the tab name matches exactly.
 */
const SPREADSHEET_ID = "1V_xuLitB-LKjKA0qHdTAps6ez76FHBbkv3AUegRM7o0";
const TARGET_SHEET_NAME = "Linkedin Job Tracker Sheet";

function getTargetSheet() {
  // Use openById to ensure the script finds the sheet during API calls
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  let sheet = ss.getSheetByName(TARGET_SHEET_NAME);

  if (!sheet) {
    sheet = ss.insertSheet(TARGET_SHEET_NAME);
    sheet.appendRow(["ID", "Job URL", "Company Name", "Description", "Status", "Last Updated", "Last Modified"]);
  }
  return sheet;
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    const sheet = getTargetSheet();
    const data = JSON.parse(e.postData.contents);

    // Check if this is a delete or update request
    const action = e.parameter.action;

    if (action === 'deleteJob') {
      return handleDeleteJob(sheet, data);
    }

    if (action === 'updateJob') {
      return handleUpdateJob(sheet, data);
    }

    // Default: Upload new job (or update if URL exists)
    const lastRow = sheet.getLastRow();
    let existingRowIndex = -1;

    // Check for duplicates in Column B (Job URL)
    if (lastRow > 1) {
      const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat();
      existingRowIndex = urls.indexOf(data.jobUrl);
    }

    if (existingRowIndex !== -1) {
      // Update existing row
      const row = existingRowIndex + 2;
      sheet.getRange(row, 3).setValue(data.companyName);
      sheet.getRange(row, 4).setValue(data.jobDescription);
      sheet.getRange(row, 5).setValue(data.status);
      sheet.getRange(row, 6).setValue(new Date());
      sheet.getRange(row, 7).setValue(data.lastModified || new Date().getTime());
    } else {
      // Append new row
      sheet.appendRow([
        data.id,
        data.jobUrl,
        data.companyName,
        data.jobDescription,
        data.status,
        new Date(),
        data.lastModified || new Date().getTime()
      ]);
    }

    return ContentService.createTextOutput(JSON.stringify({"result":"success"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}

function handleUpdateJob(sheet, data) {
  try {
    const lastRow = sheet.getLastRow();

    if (lastRow <= 1) {
      // No jobs exist, can't update
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "No jobs to update"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Find the row by Job URL (Column B)
    const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat();
    const rowIndex = urls.indexOf(data.jobUrl);

    if (rowIndex === -1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "Job not found"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Update the row
    const row = rowIndex + 2;
    sheet.getRange(row, 3).setValue(data.companyName);
    sheet.getRange(row, 4).setValue(data.jobDescription);
    sheet.getRange(row, 5).setValue(data.status);
    sheet.getRange(row, 6).setValue(new Date());
    sheet.getRange(row, 7).setValue(data.lastModified || new Date().getTime());

    return ContentService.createTextOutput(JSON.stringify({"result":"success", "message": "Job updated successfully"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function handleDeleteJob(sheet, data) {
  try {
    const lastRow = sheet.getLastRow();

    if (lastRow <= 1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "No jobs to delete"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Find the row by Job URL (Column B)
    const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat();
    const rowIndex = urls.indexOf(data.jobUrl);

    if (rowIndex === -1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "Job not found"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Delete the row (rowIndex + 2 because: +1 for header, +1 for 0-based to 1-based)
    sheet.deleteRow(rowIndex + 2);

    return ContentService.createTextOutput(JSON.stringify({"result":"success", "message": "Job deleted successfully"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet() {
  try {
    const sheet = getTargetSheet();
    const rows = sheet.getDataRange().getValues();

    if (rows.length <= 1) {
      return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
    }

    rows.shift(); // Remove headers

    const json = rows.map(row => {
      // Robust conversion for timestamp and lastModified
      const convertToTimestamp = (value) => {
        if (typeof value === 'number') {
          return value; // Already a number
        } else if (value instanceof Date) {
          return value.getTime(); // Convert Date to milliseconds
        } else if (typeof value === 'string') {
          // Try parsing as Date string, otherwise return current time
          try {
            return new Date(value).getTime();
          } catch (e) {
            return new Date().getTime();
          }
        } else {
          return new Date().getTime(); // Fallback to current time
        }
      };

      return {
        id: row[0],
        jobUrl: row[1],
        companyName: row[2],
        jobDescription: row[3],
        status: row[4],
        timestamp: convertToTimestamp(row[5]),
        lastModified: convertToTimestamp(row[6])
      };
    });

    return ContentService.createTextOutput(JSON.stringify(json))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"error": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}