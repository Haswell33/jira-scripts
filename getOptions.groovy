def issue = issueManager.getIssueObject('TEST-1')
def field = customFieldManager.getCustomFieldObject(11902)
def fieldConfig = field.getRelevantConfig(issue)
def options = optionsManager.getOptions(fieldConfig)
for (options in options)
  log.warn(place)
