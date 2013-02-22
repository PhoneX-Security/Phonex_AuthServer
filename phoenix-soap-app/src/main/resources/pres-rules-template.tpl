<?xml version="1.0" ?>
<cp:ruleset xmlns:cp="urn:ietf:params:xml:ns:common-policy" xmlns:ocp="urn:oma:xml:xdm:common-policy" xmlns:pr="urn:ietf:params:xml:ns:pres-rules">
    <cp:rule id="wp_prs_grantedcontacts">
        <cp:conditions>
               <cp:identity>
                   [[[RULES]]]
               </cp:identity>
        </cp:conditions>
        <cp:actions>
            <pr:sub-handling>allow</pr:sub-handling>
        </cp:actions>
    </cp:rule>

    <cp:rule id="wp_prs_block_all">
        <cp:conditions>
            <cp:many/>
        </cp:conditions>
        <cp:actions>
            <pr:sub-handling>block</pr:sub-handling>
        </cp:actions>
    </cp:rule>

    <cp:rule id="wp_prs_block_anonymous">
        <cp:conditions>
            <ocp:anonymous-request/>
        </cp:conditions>
        <cp:actions>
            <pr:sub-handling>block</pr:sub-handling>
        </cp:actions>
    </cp:rule>
</cp:ruleset>

