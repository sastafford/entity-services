xquery version "1.0-ml";
module namespace test-3-from-test-7 
    = "http://marklogic.com/test/Test-3-0.0.1-from-Test-7-0.0.1";

import module namespace es = "http://marklogic.com/entity-services" 
    at "/MarkLogic/entity-services/entity-services.xqy";

(: This module was generated by MarkLogic Entity Services.                          :)
(: Its purpose is to create instances of entity types                               :)
(: defined in                                                                       :)
(: Test-3, version 0.0.1                                                            :)
(: from documents that were persisted according to model                            :)
(: Test-7, version 0.0.1                                                            :)
(:                                                                                  :)
(: Modification History:                                                            :)
(: Generated at timestamp: 2016-08-08T14:40:45.007149-07:00                         :)
(:   Persisted by AUTHOR                                                            :)
(:   Date: DATE                                                                     :)


(:~
 : Creates a map:map instance representation of the target entity type
 : from a document that contains the source entity instance.
 : @param $source-node  A document or node that contains data conforming to the
 : source entity type
 : @return A map:map instance that holds the data for this entity type.
 :)
declare function test-3-from-test-7:convert-instance-Customer(
    $source-node as node()
) as map:map
{
 json:object()
(: The following line identifies the type of this instance.  Do not change it.      :)
 =>map:with('$type', 'Customer')
(: The following lines are generated from the 'Customer' entity type.               :)
 =>   map:with('CustomerId',             xs:string($source-node/Customer/CustomerId))
 =>es:optional('CompanyName',            xs:string($source-node/Customer/CompanyName))
 =>es:optional('Country',                xs:string($source-node/Customer/Country))
 =>es:optional('ContactName',            xs:string($source-node/Customer/ContactName))

};
    
(:~
 : Creates a map:map instance representation of the target entity type
 : from a document that contains the source entity instance.
 : @param $source-node  A document or node that contains data conforming to the
 : source entity type
 : @return A map:map instance that holds the data for this entity type.
 :)
declare function test-3-from-test-7:convert-instance-Product(
    $source-node as node()
) as map:map
{
 json:object()
(: The following line identifies the type of this instance.  Do not change it.      :)
 =>map:with('$type', 'Product')
(: The following lines are generated from the 'Product' entity type.                :)
 =>es:optional('ProductName',            xs:string($source-node/Product/ProductName))
 =>es:optional('UnitPrice',              xs:integer($source-node/Product/UnitPrice))
 =>   map:with('SupplierID',             xs:integer($source-node/Product/SupplierID))
 =>es:optional('Discontinued',           xs:boolean($source-node/Product/Discontinued))

};
    

(:~
 : This function includes an array if there are items to put in it.
 : If there are no such items, then it returns an empty sequence.
 : TODO EA-4? move to es: module
 :)
declare function test-3-from-test-7:extract-array(
    $path-to-property as item()*,
    $fn as function(*)
) as json:array?
{
    if (empty($path-to-property))
    then ()
    else json:to-array($path-to-property ! $fn(.))
};