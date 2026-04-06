# SES configuration set — a named container that lets you attach event
# destinations (SNS, CloudWatch, Kinesis) to track email bounces, complaints,
# and deliveries without changing the application code.
#
# The app's CertificationService passes this name in the SendEmail call.
# NOTE: SES is in sandbox mode by default — you must verify the sender address
# (var.ses_from_address) and recipient addresses in the SES console, or submit
# a production access request to send to arbitrary recipients.
resource "aws_sesv2_configuration_set" "main" {
  configuration_set_name = "${local.name_prefix}-config-set"

  sending_options {
    sending_enabled = true
  }

  # Suppression list: if an email hard-bounces or the recipient marks it as spam,
  # SES adds it to the account-level suppression list and won't deliver future
  # emails to that address. This protects your sender reputation automatically.
  suppression_options {
    suppressed_reasons = ["BOUNCE", "COMPLAINT"]
  }
}
