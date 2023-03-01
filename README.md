# Holodeck B2B Payload Only Delivery Method
This project provides a Holodeck B2B _delivery method_ that will only write the payloads of a _User Message_ to file. This can be useful when the business documents contain all relevant meta-data that the back-end application needs for processing the document.
Because there is no ebMS meta-data written to file there is also no possibility to indicate the possible relation between multiple payloads in a _User Message_ and it is the business application's responsibility to determine the possible relations solely based on the contents of the payloads.

**NOTE: This repository is not actively maintained any more!** 
__________________
For more information on using Holodeck B2B visit the website at http://holodeck-b2b.org  
Lead developer: Sander Fieten  
Code hosted at https://github.com/holodeck-b2b/payloadonly-delivery  
Issue tracker https://github.com/holodeck-b2b/payloadonly-delivery/issues 

## Installation
### Prerequisites
This delivery method can be used in Holodeck B2B version 5.x.

### Using the delivery method
Like any delivery method this one is configured in the P-Mode. As it to be used for the delivery of _User Messages_ it should be configured as the _default_ delivery method in, i.e. in element `//Leg/DefaultDelivery`. The class name of the to set in `DeliveryMethod` is `org.holodeckb2b.deliverymethod.file.PayloadOnly`.

There is one **required** parameter _deliveryDirectory_ which indicates the path of the directory where the payload files should be written to. This directory must already exists and of course should be accessible and writable by Holodeck B2B.

**NOTES:**
* Because this delivery method only delivers the business data to the back-end appliction it can only be used for the delivery of _User Message_ message units. If used in a P-Mode that also needs to notify signal message units (Receipts or Errors) to the back-end specific delivery methods should be specified for the signals!
* The back-end application should acquire a write lock on the files before processing them as Holodeck B2B may still be writing data to them.
* As payloads are delivered individually the deliverer cannot ensure that the total delivery is atomic. The {@link IMessageDeliveryEvent} may be used to get notifications about the completeness of the delivery.
* It is *recommended* to use the _duplicate elimination_ function of the _AS4 Reception Awareness_ feature.

## Contributing
Due to limited interest this extension is not actively maintained any more and only works on Holodeck B2B version 5.x. 
If you require this extension to be updated to work with newer Holodeck B2B, please contact us about sponsorship of this development work.

## Versioning
Version numbering follows the [Semantic versioning](http://semver.org/) approach.

## License
Like the Holodeck B2B Core this delivery method is licensed under the General Public License V3 (GPLv3) which is included in the [LICENSE](LICENCE) file in the root of the project.

## Support
Commercial Holodeck B2B support is provided by Chasquis Consulting. Visit [Chasquis-Consulting.com](http://chasquis-consulting.com/holodeck-b2b-support/) for more information.
